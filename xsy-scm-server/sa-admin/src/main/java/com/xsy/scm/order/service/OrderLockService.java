package com.xsy.scm.order.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.order.constant.ScmOrderLockReasonEnum;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.form.OrderLockForm;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_LOCKED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_NOT_FOUND;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_STATE_INVALID;

/**
 * 锁单不是订单状态，只是「改单需要额外授权」的开关：锁住后状态仍是已确认，配送、对账和商城展示都不受影响。
 *
 * <p>
 * 锁单由财务/管理员手动办理，或由发车满 7 天的定时任务自动办理；解锁是独立权限，避免同一角色随手把锁解开。
 *
 * <p>
 * 定时任务的操作人写成 {@value #AUTO_LOCK_OPERATOR}：任务没有登录上下文，而操作日志必须回答「谁锁的」。
 */
@Service
@RequiredArgsConstructor
public class OrderLockService {
    /** 自动锁单在操作日志与 {@code locked_by} 中使用的系统操作人。 */
    public static final String AUTO_LOCK_OPERATOR = "system:order-auto-lock";

    /** 锁单原因码：手动锁单。 */
    public static final String MANUAL_REASON = ScmOrderLockReasonEnum.MANUAL.name();

    /** 锁单原因码：发车满 7 天自动锁单。 */
    public static final String AUTO_7_DAYS_REASON = ScmOrderLockReasonEnum.AUTO_7_DAYS.name();

    /** 自动锁单起算天数：发车出库后留出改单窗口。 */
    public static final int AUTO_LOCK_DAYS = 7;

    private final SalesOrderDao salesOrderDao;
    private final SalesOrderQueryService salesOrderQueryService;
    private final OrderOperationLogRecorder orderLogs;
    private final ScmDataScopeService dataScopeService;

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO lock(OrderLockForm form) {
        var order = lockForWrite(form);
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(order.getStatus()))
            throw new ScmBusinessException(ORDER_STATE_INVALID);
        if (Boolean.TRUE.equals(order.getLockedFlag()))
            throw new ScmBusinessException(ORDER_LOCKED);
        if (salesOrderDao.lockIfUnlocked(order.getId(), OffsetDateTime.now(), ScmOperator.current(), MANUAL_REASON,
            null) != 1)
            throw new ScmBusinessException(ORDER_LOCKED);
        var result = salesOrderQueryService.detailSnapshot(order.getId());
        orderLogs.record(order.getId(), ScmOrderOperationTypeEnum.LOCK, reason(form.getReason()), null,
            lockedSnapshot(result));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO unlock(OrderLockForm form) {
        var order = lockForWrite(form);
        if (!Boolean.TRUE.equals(order.getLockedFlag()))
            throw new ScmBusinessException(ORDER_STATE_INVALID);
        if (salesOrderDao.unlock(order.getId(), ScmOperator.current()) != 1)
            throw new ScmBusinessException(ORDER_STATE_INVALID);
        var result = salesOrderQueryService.detailSnapshot(order.getId());
        orderLogs.record(order.getId(), ScmOrderOperationTypeEnum.UNLOCK, reason(form.getReason()),
            snapshot(true, order.getLockReason(), order.getLockedAt()), snapshot(false, null, null));
        return result;
    }

    /**
     * 自动锁单一条订单，返回本次是否真的锁上。逐条调用、逐条事务：一条订单的并发修改或异常不应该拖住整批。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean autoLock(Long orderId) {
        var now = OffsetDateTime.now();
        if (salesOrderDao.lockIfUnlocked(orderId, now, AUTO_LOCK_OPERATOR, AUTO_7_DAYS_REASON, now) != 1)
            return false;
        var result = salesOrderQueryService.detailSnapshot(orderId);
        orderLogs.record(orderId, ScmOrderOperationTypeEnum.AUTO_LOCK, "发车出库满 " + AUTO_LOCK_DAYS + " 天自动锁单", null,
            lockedSnapshot(result), AUTO_LOCK_OPERATOR);
        return true;
    }

    /**
     * 写路径取行：范围与功能权限是两层独立校验，凭订单 ID 越权锁单必须在这里失败。
     */
    private SalesOrderEntity lockForWrite(OrderLockForm form) {
        var order = salesOrderDao.lock(form.getOrderId());
        if (order == null)
            throw new ScmBusinessException(ORDER_NOT_FOUND);
        if (!dataScopeService.resolve().getOrderSellerScope().allows(order.getSellerId()))
            throw new ScmDataScopeException();
        SalesOrderService.version(order.getVersion(), form.getVersion());
        return order;
    }

    private static String reason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private static Map<String, Object> lockedSnapshot(SalesOrderDetailVO result) {
        return snapshot(Boolean.TRUE.equals(result.getLockedFlag()), result.getLockReason(), result.getLockedAt());
    }

    private static Map<String, Object> snapshot(boolean locked, String lockReason, OffsetDateTime lockedAt) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("lockedFlag", locked);
        snapshot.put("lockReason", lockReason);
        snapshot.put("lockedAt", lockedAt);
        return snapshot;
    }
}
