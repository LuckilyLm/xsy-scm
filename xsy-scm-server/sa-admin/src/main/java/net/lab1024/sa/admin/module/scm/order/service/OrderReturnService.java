package net.lab1024.sa.admin.module.scm.order.service;

import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import net.lab1024.sa.admin.module.scm.order.dao.*;
import net.lab1024.sa.admin.module.scm.order.manager.*;
import net.lab1024.sa.admin.module.scm.order.constant.ScmOrderOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;

import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Function;

import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

/**
 * Serialize every after-sales write on the original order, before return/refund locks.
 */
@Service
@RequiredArgsConstructor
public class OrderReturnService {
    private final SalesOrderService orders;
    private final SalesOrderItemDao orderItems;
    private final OrderReturnDao returns;
    private final OrderReturnItemDao items;
    private final OrderRefundDao refunds;
    private final OrderNumberGenerator numbers;
    private final OrderIdempotencyService idempotency;
    private final OrderOperationLogRecorder orderLogs;

    public PageResult<OrderReturnVO> query(OrderReturnQueryForm f) {
        var page = SmartPageUtil.convert2PageQuery(f);
        return SmartPageUtil.convert2PageResult(page, returns.query(page, f).stream().map(this::vo).toList());
    }

    private OrderReturnVO vo(OrderReturnEntity r) {
        var v = new OrderReturnVO();
        BeanUtils.copyProperties(r, v);
        v.setReturnId(r.getId());
        return v;
    }

    public OrderReturnDetailVO detail(Long id) {
        var r = returns.selectById(id);
        if (r == null) throw new ScmBusinessException(ORDER_RETURN_NOT_FOUND);
        var v = new OrderReturnDetailVO();
        BeanUtils.copyProperties(vo(r), v);
        v.setItems(items.list(id).stream().map(x -> {
            var i = new OrderReturnItemVO();
            BeanUtils.copyProperties(x, i);
            i.setReturnItemId(x.getId());
            return i;
        }).toList());
        return v;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO create(OrderReturnAddForm f, String key) {
        var claim = idempotency.claim("ORDER_RETURN_CREATE", key, f);
        if (claim.replay()) return idempotency.replay(claim, OrderReturnDetailVO.class);
        var order = orders.lock(f.getOrderId());
        if (!"CONFIRMED".equals(order.getStatus())) throw new ScmBusinessException(ORDER_RETURN_ORDER_NOT_CONFIRMED);
        OrderValidator.reason(f.getReason(), ORDER_RETURN_ITEM_INVALID);
        var originals = orderItems.list(order.getId()).stream().collect(Collectors.toMap(SalesOrderItemEntity::getId, Function.identity()));
        var r = new OrderReturnEntity();
        r.setOrderId(order.getId());
        r.setCustomerId(order.getCustomerId());
        r.setReturnNo(numbers.returned());
        r.setStatus("PENDING");
        r.setReason(f.getReason().trim());
        r.setApprovedAmount(BigDecimal.ZERO.setScale(4));
        stamp(r, true);
        var pending = new ArrayList<OrderReturnItemEntity>();
        var seen = new HashSet<Long>();
        for (var x : f.getItems()) {
            var original = originals.get(x.getOrderItemId());
            if (original == null || !seen.add(x.getOrderItemId()))
                throw new ScmBusinessException(ORDER_RETURN_ITEM_INVALID);
            // Lock order item explicitly; all competing return commands follow the same order-first lock sequence.
            orderItems.lock(original.getId());
            var quantity = OrderValidator.decimal(x.getRequestedQuantity(), true);
            if (original.getActualQuantity() == null || items.reserved(original.getId()).add(quantity).compareTo(original.getActualQuantity()) > 0)
                throw new ScmBusinessException(ORDER_RETURN_QUANTITY_EXCEEDED);
            var row = new OrderReturnItemEntity();
            row.setOrderItemId(original.getId());
            row.setRequestedQuantity(quantity);
            row.setLockedUnitPrice(original.getLockedUnitPrice());
            row.setApprovedAmount(BigDecimal.ZERO.setScale(4));
            row.setCreatedAt(OffsetDateTime.now());
            row.setCreatedBy(ScmOperator.current());
            row.setUpdatedAt(row.getCreatedAt());
            row.setUpdatedBy(row.getCreatedBy());
            pending.add(row);
        }
        if (pending.isEmpty()) throw new ScmBusinessException(ORDER_RETURN_ITEM_INVALID);
        returns.insert(r);
        for (var row : pending) {
            row.setReturnId(r.getId());
            items.insert(row);
        }
        var result = detail(r.getId());
        // §7.3：return 与 cancellation / refund 并列，必须留操作日志。日志与业务变更同一事务。
        orderLogs.record(r.getOrderId(), ScmOrderOperationTypeEnum.RETURN,
                "退货单 " + r.getReturnNo() + " 建单", null, Map.of("status", result.getStatus()));
        idempotency.complete(claim, "ORDER_RETURN", r.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO approve(OrderReturnApproveForm f, String key) {
        var claim = idempotency.claim("ORDER_RETURN_APPROVE:" + f.getReturnId(), key, f);
        if (claim.replay()) return idempotency.replay(claim, OrderReturnDetailVO.class);
        var r = lock(f.getReturnId());
        SalesOrderService.version(r.getVersion(), f.getVersion());
        pending(r);
        var rows = items.list(r.getId());
        var quantities = new HashMap<Long, BigDecimal>();
        for (var x : f.getItems()) {
            if (quantities.put(x.getOrderItemId(), OrderValidator.decimal(x.getApprovedQuantity(), false)) != null)
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        }
        if (quantities.size() != rows.size()) throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        for (var row : rows) {
            var qty = quantities.get(row.getOrderItemId());
            if (qty == null || qty.compareTo(row.getRequestedQuantity()) > 0)
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
            row.setApprovedQuantity(qty);
            row.setApprovedAmount(OrderAmountCalculator.lineAmount(qty, row.getLockedUnitPrice()));
        }
        var total = OrderAmountCalculator.orderAmount(rows.stream().map(OrderReturnItemEntity::getApprovedAmount).toList());
        if (total.signum() <= 0) throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        for (var row : rows) {
            row.setUpdatedAt(OffsetDateTime.now());
            row.setUpdatedBy(ScmOperator.current());
            if (items.updateById(row) != 1) throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
        }
        r.setApprovedAmount(total);
        r.setStatus("APPROVED");
        r.setApprovedAt(OffsetDateTime.now());
        stamp(r, false);
        if (returns.updateById(r) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        var refund = new OrderRefundEntity();
        refund.setRefundNo(numbers.refund());
        refund.setReturnId(r.getId());
        refund.setOrderId(r.getOrderId());
        refund.setCustomerId(r.getCustomerId());
        refund.setRefundAmount(total);
        refund.setStatus("PENDING");
        refund.setCreatedAt(OffsetDateTime.now());
        refund.setUpdatedAt(refund.getCreatedAt());
        refund.setCreatedBy(ScmOperator.current());
        refund.setUpdatedBy(refund.getCreatedBy());
        refunds.insert(refund);
        var result = detail(r.getId());
        orderLogs.record(r.getOrderId(), ScmOrderOperationTypeEnum.RETURN,
                "退货单 " + r.getReturnNo() + " 审批通过，并生成退款单 " + refund.getRefundNo(),
                Map.of("status", "PENDING"), Map.of("status", result.getStatus(),
                        "approvedAmount", String.valueOf(refund.getRefundAmount())));
        idempotency.complete(claim, "ORDER_RETURN", r.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO reject(OrderReturnDecisionForm f, String key) {
        return decide(f, key, "REJECTED");
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO cancel(OrderReturnDecisionForm f, String key) {
        return decide(f, key, "CANCELLED");
    }

    private OrderReturnDetailVO decide(OrderReturnDecisionForm f, String key, String state) {
        var claim = idempotency.claim("ORDER_RETURN_" + state + ":" + f.getReturnId(), key, f);
        if (claim.replay()) return idempotency.replay(claim, OrderReturnDetailVO.class);
        var r = lock(f.getReturnId());
        SalesOrderService.version(r.getVersion(), f.getVersion());
        pending(r);
        OrderValidator.reason(f.getDecisionReason(), ORDER_RETURN_APPROVAL_INVALID);
        r.setStatus(state);
        r.setDecisionReason(f.getDecisionReason().trim());
        if ("REJECTED".equals(state)) r.setRejectedAt(OffsetDateTime.now());
        else r.setCancelledAt(OffsetDateTime.now());
        stamp(r, false);
        if (returns.updateById(r) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        var result = detail(r.getId());
        orderLogs.record(r.getOrderId(), ScmOrderOperationTypeEnum.RETURN,
                f.getDecisionReason().trim(), Map.of("status", "PENDING"),
                Map.of("status", state, "decisionReason", r.getDecisionReason()));
        idempotency.complete(claim, "ORDER_RETURN", r.getId(), result);
        return result;
    }

    private OrderReturnEntity lock(Long id) {
        var r = returns.selectById(id);
        if (r == null) throw new ScmBusinessException(ORDER_RETURN_NOT_FOUND);
        orders.lock(r.getOrderId());
        for (var item : orderItems.list(r.getOrderId())) orderItems.lock(item.getId());
        return returns.lock(id);
    }

    private void pending(OrderReturnEntity r) {
        if (!"PENDING".equals(r.getStatus())) throw new ScmBusinessException(ORDER_RETURN_STATUS_INVALID);
    }

    private void stamp(OrderReturnEntity r, boolean creating) {
        r.setUpdatedAt(OffsetDateTime.now());
        r.setUpdatedBy(ScmOperator.current());
        if (creating) {
            r.setCreatedAt(r.getUpdatedAt());
            r.setCreatedBy(r.getUpdatedBy());
        }
    }
}
