package com.xsy.scm.order.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import com.xsy.scm.finance.service.FinanceReceivableService;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderRefundStatusEnum;
import com.xsy.scm.order.constant.ScmOrderReturnStatusEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.dao.OrderRefundDao;
import com.xsy.scm.order.dao.OrderReturnDao;
import com.xsy.scm.order.dao.OrderReturnItemDao;
import com.xsy.scm.order.dao.OrderReturnReceiptItemDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.entity.OrderRefundEntity;
import com.xsy.scm.order.domain.entity.OrderReturnEntity;
import com.xsy.scm.order.domain.entity.OrderReturnItemEntity;
import com.xsy.scm.order.domain.entity.OrderReturnReceiptItemEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnDecisionForm;
import com.xsy.scm.order.domain.form.OrderReturnQueryForm;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.domain.vo.OrderReturnItemVO;
import com.xsy.scm.order.domain.vo.OrderReturnReceiptVO;
import com.xsy.scm.order.domain.vo.OrderReturnVO;
import com.xsy.scm.order.manager.OrderAmountCalculator;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import com.xsy.scm.order.manager.OrderValidator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_VERSION_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_APPROVAL_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_ITEM_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_NOT_FOUND;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_ORDER_NOT_CONFIRMED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_QUANTITY_EXCEEDED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_STATUS_INVALID;

/**
 * Serialize every after-sales write on the original order, before return/refund locks.
 */
@Service
@RequiredArgsConstructor
public class OrderReturnService {
    private final SalesOrderService salesOrderService;
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderReturnDao orderReturnDao;
    private final OrderReturnItemDao orderReturnItemDao;
    private final OrderReturnReceiptItemDao orderReturnReceiptItemDao;
    private final OrderRefundDao orderRefundDao;
    private final OrderNumberGenerator numbers;
    private final OrderIdempotencyService orderIdempotencyService;
    private final OrderOperationLogRecorder orderLogs;
    private final ScmDataScopeService dataScopeService;
    /**
     * 红字应收生成器。依赖方向是 order → finance， finance 对订单与退货表只读、不反向 import 订单域，因此不构成环；生成失败即整笔批准回滚（与库存/幂等写入同一事务纪律）。
     */
    private final FinanceReceivableService financeReceivableService;

    public PageResult<OrderReturnVO> query(OrderReturnQueryForm orderReturnQueryForm) {
        ScmDataScopeContext dataScopeContext = dataScopeService.resolve();
        if (dataScopeContext.getOrderSellerScope().isEmpty())
            return ScmDataScopeService.emptyPage(orderReturnQueryForm);
        var page = SmartPageUtil.convert2PageQuery(orderReturnQueryForm);
        return SmartPageUtil.convert2PageResult(page,
                orderReturnDao.query(page, orderReturnQueryForm, dataScopeContext.getOrderSellerScope()));
    }

    private OrderReturnVO vo(OrderReturnEntity orderReturnEntity) {
        var orderReturnResultVO = new OrderReturnVO();
        BeanUtils.copyProperties(orderReturnEntity, orderReturnResultVO);
        orderReturnResultVO.setReturnId(orderReturnEntity.getId());
        var order = salesOrderDao.selectById(orderReturnEntity.getOrderId());
        if (order != null && !Boolean.TRUE.equals(order.getDeleted())) {
            orderReturnResultVO.setOrderNo(order.getOrderNo());
            orderReturnResultVO.setCustomerName(order.getCustomerNameSnapshot());
        }
        return orderReturnResultVO;
    }

    /** 退货单详情读（HTTP 入口）：可见性跟随父订单的负责人范围。 */
    public OrderReturnDetailVO detail(Long orderReturnId) {
        return detail(orderReturnId, dataScopeService.resolve());
    }

    /**
     * 退货单详情读 + 显式范围。父订单读不到时同样按 30005 处理：退货单本身没有归属列，「看不到订单却能看它的退货」就是绕过。
     */
    public OrderReturnDetailVO detail(Long orderReturnId, ScmDataScopeContext dataScopeContext) {
        var orderReturnEntity = orderReturnDao.selectById(orderReturnId);
        if (orderReturnEntity == null)
            throw new ScmBusinessException(ORDER_RETURN_NOT_FOUND);
        requireParentOrderVisible(orderReturnEntity.getOrderId(), dataScopeContext.getOrderSellerScope());
        return detailSnapshot(orderReturnEntity);
    }

    /**
     * 未收窄的详情快照：审批/驳回/取消等写命令在同一事务里回读自己刚改过的单据，归属判定只属于读接口，不给写流程加第二次门槛（写流程的门槛在订单锁与状态机上）。
     */
    public OrderReturnDetailVO detailSnapshot(Long orderReturnId) {
        var orderReturnEntity = orderReturnDao.selectById(orderReturnId);
        if (orderReturnEntity == null)
            throw new ScmBusinessException(ORDER_RETURN_NOT_FOUND);
        return detailSnapshot(orderReturnEntity);
    }

    private OrderReturnDetailVO detailSnapshot(OrderReturnEntity orderReturnEntity) {
        var orderReturnResultVO = new OrderReturnDetailVO();
        BeanUtils.copyProperties(vo(orderReturnEntity), orderReturnResultVO);
        var orderItems = salesOrderItemDao.list(orderReturnEntity.getOrderId()).stream()
                .collect(Collectors.toMap(SalesOrderItemEntity::getId, Function.identity()));
        orderReturnResultVO.setItems(orderReturnItemDao.list(orderReturnEntity.getId()).stream().map(returnItem -> {
            var returnItemVO = new OrderReturnItemVO();
            BeanUtils.copyProperties(returnItem, returnItemVO);
            returnItemVO.setReturnItemId(returnItem.getId());
            returnItemVO.setReceivedQuantity(orderReturnReceiptItemDao.receivedQuantity(returnItem.getId()));
            returnItemVO.setReceiptAllocations(orderReturnReceiptItemDao.listByReturnItemId(returnItem.getId()).stream()
                    .map(this::receiptAllocation).toList());
            var orderItem = orderItems.get(returnItem.getOrderItemId());
            if (orderItem != null) {
                returnItemVO.setProductName(orderItem.getProductNameSnapshot());
                returnItemVO.setUnit(orderItem.getSaleUnitSnapshot());
            }
            return returnItemVO;
        }).toList());
        return orderReturnResultVO;
    }

    private OrderReturnReceiptVO.Item receiptAllocation(OrderReturnReceiptItemEntity entity) {
        var item = new OrderReturnReceiptVO.Item();
        item.setReceiptItemId(entity.getId());
        item.setReturnItemId(entity.getReturnItemId());
        item.setSourceSalesOutMovementId(entity.getSourceSalesOutMovementId());
        item.setSourceOutboundItemId(entity.getSourceOutboundItemId());
        item.setDisposition(entity.getDisposition());
        item.setQuantity(entity.getQuantity());
        item.setUnit(entity.getUnitSnapshot());
        item.setUnitCost(entity.getUnitCost());
        return item;
    }

    private void requireParentOrderVisible(Long orderId, ScmValueScope orderSellerScope) {
        var order = salesOrderDao.selectById(orderId);
        if (order == null || !orderSellerScope.allows(order.getSellerId())) {
            throw new ScmDataScopeException();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO create(OrderReturnAddForm orderReturnAddForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_RETURN_CREATE", key, orderReturnAddForm);
        if (claim.replay())
            return orderIdempotencyService.replay(claim, OrderReturnDetailVO.class);
        var order = salesOrderService.lock(orderReturnAddForm.getOrderId());
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(order.getStatus()))
            throw new ScmBusinessException(ORDER_RETURN_ORDER_NOT_CONFIRMED);
        OrderValidator.reason(orderReturnAddForm.getReason(), ORDER_RETURN_ITEM_INVALID);
        var originals = salesOrderItemDao.list(order.getId()).stream()
                .collect(Collectors.toMap(SalesOrderItemEntity::getId, Function.identity()));
        var orderReturnEntity = new OrderReturnEntity();
        orderReturnEntity.setOrderId(order.getId());
        orderReturnEntity.setCustomerId(order.getCustomerId());
        orderReturnEntity.setReturnNo(numbers.returned());
        orderReturnEntity.setStatus(ScmOrderReturnStatusEnum.PENDING.name());
        orderReturnEntity.setReason(orderReturnAddForm.getReason().trim());
        orderReturnEntity.setApprovedAmount(BigDecimal.ZERO.setScale(4));
        stamp(orderReturnEntity, true);
        var pending = new ArrayList<OrderReturnItemEntity>();
        var seen = new HashSet<Long>();
        for (var returnItem : orderReturnAddForm.getItems()) {
            var original = originals.get(returnItem.getOrderItemId());
            if (original == null || !seen.add(returnItem.getOrderItemId()))
                throw new ScmBusinessException(ORDER_RETURN_ITEM_INVALID);
            // Lock order item explicitly; all competing return commands follow the same order-first lock sequence.
            salesOrderItemDao.lock(original.getId());
            var quantity = OrderValidator.decimal(returnItem.getRequestedQuantity(), true);
            if (original.getActualQuantity() == null || orderReturnItemDao.reserved(original.getId()).add(quantity)
                    .compareTo(original.getActualQuantity()) > 0)
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
        if (pending.isEmpty())
            throw new ScmBusinessException(ORDER_RETURN_ITEM_INVALID);
        orderReturnDao.insert(orderReturnEntity);
        for (var row : pending) {
            row.setReturnId(orderReturnEntity.getId());
            orderReturnItemDao.insert(row);
        }
        var result = detailSnapshot(orderReturnEntity.getId());
        // 退货与取消、退款一样必须留操作日志，日志与业务变更使用同一事务。
        orderLogs.record(orderReturnEntity.getOrderId(), ScmOrderOperationTypeEnum.RETURN,
                "退货单 " + orderReturnEntity.getReturnNo() + " 建单", null, Map.of("status", result.getStatus()));
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name(),
                orderReturnEntity.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO approve(OrderReturnApproveForm orderReturnApproveForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_RETURN_APPROVE:" + orderReturnApproveForm.getReturnId(), key,
                orderReturnApproveForm);
        if (claim.replay())
            return orderIdempotencyService.replay(claim, OrderReturnDetailVO.class);
        var orderReturnEntity = lock(orderReturnApproveForm.getReturnId());
        SalesOrderService.version(orderReturnEntity.getVersion(), orderReturnApproveForm.getVersion());
        pending(orderReturnEntity);
        var rows = orderReturnItemDao.list(orderReturnEntity.getId());
        var quantities = new HashMap<Long, BigDecimal>();
        for (var returnItem : orderReturnApproveForm.getItems()) {
            if (quantities.put(returnItem.getOrderItemId(),
                    OrderValidator.decimal(returnItem.getApprovedQuantity(), false)) != null)
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        }
        if (quantities.size() != rows.size())
            throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        for (var row : rows) {
            var qty = quantities.get(row.getOrderItemId());
            if (qty == null || qty.compareTo(row.getRequestedQuantity()) > 0)
                throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
            row.setApprovedQuantity(qty);
            row.setApprovedAmount(OrderAmountCalculator.lineAmount(qty, row.getLockedUnitPrice()));
        }
        var total = OrderAmountCalculator
                .orderAmount(rows.stream().map(OrderReturnItemEntity::getApprovedAmount).toList());
        if (total.signum() <= 0)
            throw new ScmBusinessException(ORDER_RETURN_APPROVAL_INVALID);
        for (var row : rows) {
            row.setUpdatedAt(OffsetDateTime.now());
            row.setUpdatedBy(ScmOperator.current());
            if (orderReturnItemDao.updateById(row) != 1)
                throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
        }
        orderReturnEntity.setApprovedAmount(total);
        orderReturnEntity.setStatus(ScmOrderReturnStatusEnum.APPROVED.name());
        orderReturnEntity.setApprovedAt(OffsetDateTime.now());
        stamp(orderReturnEntity, false);
        if (orderReturnDao.updateById(orderReturnEntity) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        var refund = new OrderRefundEntity();
        refund.setRefundNo(numbers.refund());
        refund.setReturnId(orderReturnEntity.getId());
        refund.setOrderId(orderReturnEntity.getOrderId());
        refund.setCustomerId(orderReturnEntity.getCustomerId());
        refund.setRefundAmount(total);
        refund.setStatus(ScmOrderRefundStatusEnum.PENDING.name());
        refund.setCreatedAt(OffsetDateTime.now());
        refund.setUpdatedAt(refund.getCreatedAt());
        refund.setCreatedBy(ScmOperator.current());
        refund.setUpdatedBy(refund.getCreatedBy());
        orderRefundDao.insert(refund);
        var result = detailSnapshot(orderReturnEntity.getId());
        orderLogs.record(orderReturnEntity.getOrderId(), ScmOrderOperationTypeEnum.RETURN,
                "退货单 " + orderReturnEntity.getReturnNo() + " 审批通过，并生成退款单 " + refund.getRefundNo(),
                Map.of("status", ScmOrderReturnStatusEnum.PENDING.name()),
                Map.of("status", result.getStatus(), "approvedAmount", String.valueOf(refund.getRefundAmount())));

        // 退货批准是红字应收的业务来源，在退货事实与退款单都已成立后、幂等 complete 前生成。
        // 红字生成失败会让整笔 approve 回滚，
        // 但「正常应收还不存在」是成功跳过（签收时补生成），绝不阻塞这里。
        // 生成器不做任何金额上限校验：财务规则不得反向控制订单域状态机。
        // 本类持有的 sales_order 行锁（lock() 里 salesOrderService.lock）就是它与签收之间的串行点。
        financeReceivableService.generateRedOnReturnApproved(orderReturnEntity.getId());

        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name(),
                orderReturnEntity.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO reject(OrderReturnDecisionForm orderReturnDecisionForm, String key) {
        return decide(orderReturnDecisionForm, key, ScmOrderReturnStatusEnum.REJECTED);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderReturnDetailVO cancel(OrderReturnDecisionForm orderReturnDecisionForm, String key) {
        return decide(orderReturnDecisionForm, key, ScmOrderReturnStatusEnum.CANCELLED);
    }

    private OrderReturnDetailVO decide(OrderReturnDecisionForm orderReturnDecisionForm, String key,
            ScmOrderReturnStatusEnum state) {
        var claim = orderIdempotencyService.claim(
                "ORDER_RETURN_" + state.name() + ":" + orderReturnDecisionForm.getReturnId(), key,
                orderReturnDecisionForm);
        if (claim.replay())
            return orderIdempotencyService.replay(claim, OrderReturnDetailVO.class);
        var orderReturnEntity = lock(orderReturnDecisionForm.getReturnId());
        SalesOrderService.version(orderReturnEntity.getVersion(), orderReturnDecisionForm.getVersion());
        pending(orderReturnEntity);
        OrderValidator.reason(orderReturnDecisionForm.getDecisionReason(), ORDER_RETURN_APPROVAL_INVALID);
        orderReturnEntity.setStatus(state.name());
        orderReturnEntity.setDecisionReason(orderReturnDecisionForm.getDecisionReason().trim());
        if (ScmOrderReturnStatusEnum.REJECTED == state)
            orderReturnEntity.setRejectedAt(OffsetDateTime.now());
        else
            orderReturnEntity.setCancelledAt(OffsetDateTime.now());
        stamp(orderReturnEntity, false);
        if (orderReturnDao.updateById(orderReturnEntity) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        var result = detailSnapshot(orderReturnEntity.getId());
        orderLogs.record(orderReturnEntity.getOrderId(), ScmOrderOperationTypeEnum.RETURN,
                orderReturnDecisionForm.getDecisionReason().trim(),
                Map.of("status", ScmOrderReturnStatusEnum.PENDING.name()),
                Map.of("status", state.name(), "decisionReason", orderReturnEntity.getDecisionReason()));
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name(),
                orderReturnEntity.getId(), result);
        return result;
    }

    public OrderReturnEntity lockForReceipt(Long orderReturnId) {
        return lock(orderReturnId);
    }

    private OrderReturnEntity lock(Long orderReturnId) {
        var orderReturnEntity = orderReturnDao.selectById(orderReturnId);
        if (orderReturnEntity == null)
            throw new ScmBusinessException(ORDER_RETURN_NOT_FOUND);
        salesOrderService.lock(orderReturnEntity.getOrderId());
        for (var salesOrderItem : salesOrderItemDao.list(orderReturnEntity.getOrderId())) {
            salesOrderItemDao.lock(salesOrderItem.getId());
        }
        return orderReturnDao.lock(orderReturnId);
    }

    private void pending(OrderReturnEntity orderReturnEntity) {
        if (!ScmOrderReturnStatusEnum.PENDING.name().equals(orderReturnEntity.getStatus()))
            throw new ScmBusinessException(ORDER_RETURN_STATUS_INVALID);
    }

    private void stamp(OrderReturnEntity orderReturnEntity, boolean creating) {
        orderReturnEntity.setUpdatedAt(OffsetDateTime.now());
        orderReturnEntity.setUpdatedBy(ScmOperator.current());
        if (creating) {
            orderReturnEntity.setCreatedAt(orderReturnEntity.getUpdatedAt());
            orderReturnEntity.setCreatedBy(orderReturnEntity.getUpdatedBy());
        }
    }
}
