package net.lab1024.sa.admin.module.scm.order.service;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import net.lab1024.sa.admin.module.scm.order.dao.*;
import net.lab1024.sa.admin.module.scm.order.manager.*;
import net.lab1024.sa.admin.module.scm.order.constant.ScmOrderOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;

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

@Service
@RequiredArgsConstructor
public class OrderRefundService {
    private final OrderRefundDao refunds;
    private final SalesOrderDao orderRows;
    private final SalesOrderService orders;
    private final OrderIdempotencyService idempotency;
    private final OrderOperationLogRecorder orderLogs;
    private final ScmDataScopeService scopeService;

    public PageResult<OrderRefundVO> query(OrderRefundQueryForm f) {
        ScmDataScopeContext scope = scopeService.resolve();
        if (scope.getOrderSellerScope().isEmpty()) return ScmDataScopeService.emptyPage(f);
        var page = SmartPageUtil.convert2PageQuery(f);
        return SmartPageUtil.convert2PageResult(page,
                refunds.query(page, f, scope.getOrderSellerScope()).stream().map(this::vo).toList());
    }

    private OrderRefundVO vo(OrderRefundEntity r) {
        var v = new OrderRefundVO();
        BeanUtils.copyProperties(r, v);
        v.setRefundId(r.getId());
        return v;
    }

    /** 退款单详情读（HTTP 入口）：可见性跟随父订单的负责人范围。 */
    public OrderRefundVO detail(Long id) {
        return detail(id, scopeService.resolve());
    }

    /**
     * 退款单详情读 + 显式范围。退款金额是财务事实，读不到原订单的人也不能读到它，
     * 因此父订单缺失或不在范围内都按 30005 处理。
     */
    public OrderRefundVO detail(Long id, ScmDataScopeContext scope) {
        var r = refunds.selectById(id);
        if (r == null) throw new ScmBusinessException(ORDER_REFUND_NOT_FOUND);
        var order = orderRows.selectById(r.getOrderId());
        if (order == null || !scope.getOrderSellerScope().allows(order.getSellerId())) {
            throw new ScmDataScopeException();
        }
        return vo(r);
    }

    /**
     * 未收窄的详情：退款完成命令在同一事务里取改前/改后镜像并回传结果，
     * 该路径的门槛是订单锁与状态机，不是读范围。
     */
    public OrderRefundVO detailSnapshot(Long id) {
        var r = refunds.selectById(id);
        if (r == null) throw new ScmBusinessException(ORDER_REFUND_NOT_FOUND);
        return vo(r);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderRefundVO complete(OrderRefundCompleteForm f, String key) {
        var claim = idempotency.claim("ORDER_REFUND_COMPLETE:" + f.getRefundId(), key, f);
        if (claim.replay()) return idempotency.replay(claim, OrderRefundVO.class);
        var before = detailSnapshot(f.getRefundId());
        orders.lock(before.getOrderId());
        var r = refunds.lock(f.getRefundId());
        SalesOrderService.version(r.getVersion(), f.getVersion());
        if (!"PENDING".equals(r.getStatus())) throw new ScmBusinessException(ORDER_REFUND_STATUS_INVALID);
        r.setStatus("COMPLETED");
        r.setExternalReference(OrderValidator.trim(f.getExternalReference()));
        r.setCompletedAt(OffsetDateTime.now());
        r.setUpdatedAt(r.getCompletedAt());
        r.setUpdatedBy(ScmOperator.current());
        try {
            if (refunds.updateById(r) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ScmBusinessException(ORDER_REFUND_STATUS_INVALID);
        }
        var result = detailSnapshot(r.getId());
        // §7.3：退款完成同样是必须留痕的订单状态变更。镜像取未收窄的 detailSnapshot，
        // 日志要记真实状态，而不是按调用者读范围裁过的视图。
        orderLogs.record(r.getOrderId(), ScmOrderOperationTypeEnum.REFUND,
                "退款单 " + r.getRefundNo() + " 已完成", Map.of("status", before.getStatus()),
                Map.of("status", result.getStatus()));
        idempotency.complete(claim, "ORDER_REFUND", r.getId(), result);
        return result;
    }
}
