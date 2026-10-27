package com.xsy.scm.purchase.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.constant.ScmPurchaseLockReasonEnum;
import com.xsy.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import com.xsy.scm.purchase.dao.PurchaseOperationLogDao;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.domain.entity.PurchaseOperationLogEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.form.PurchaseOrderLockForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.manager.PurchaseOrderAuditSnapshotFactory;
import com.xsy.scm.purchase.manager.PurchaseOrderValidator;
import com.xsy.scm.purchase.manager.PurchaseSnapshotFactory;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_LOCKED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_STATE_INVALID;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_UNLOCK_REASON_REQUIRED;

/**
 * 采购锁单：供应商对账完成后关掉补价/改价入口。
 *
 * <p>
 * 锁不是采购状态：收货、入库、应付都照常。它只回答「价格还能不能被改」。 提交满 7 天由定时任务自动锁上；手动锁与解锁是两条独立权限，解锁必须说明原因。
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderLockService {

    /** 价格窗口与自动锁单起算天数；与 V190/V192 的注释保持一致。 */
    public static final int PRICE_WINDOW_DAYS = 7;

    /** 系统任务没有登录上下文，操作日志用固定操作人回答「谁锁的」。 */
    public static final String AUTO_LOCK_OPERATOR = "system:purchase-auto-lock";

    private final PurchaseOrderDao purchaseOrderDao;
    private final PurchaseQueryService purchaseQueryService;
    private final PurchaseOperationLogDao purchaseOperationLogDao;
    private final PurchaseOwnerResolver purchaseOwnerResolver;

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO lock(PurchaseOrderLockForm form) {
        PurchaseOrderEntity order = lockForWrite(form);
        if (Boolean.TRUE.equals(order.getLockedFlag())) {
            throw new ScmBusinessException(PURCHASE_ORDER_LOCKED);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (purchaseOrderDao.lockIfUnlocked(order.getId(), now, ScmOperator.current(),
            ScmPurchaseLockReasonEnum.MANUAL.name(), null) != 1) {
            throw new ScmBusinessException(PURCHASE_ORDER_LOCKED);
        }
        PurchaseOrderVO result = purchaseQueryService.orderDetailForCommand(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(ScmPurchaseOperationTypeEnum.PRICE_LOCK,
            order.getId(), null, form.getReason(), lockedSnapshot(false, null, null),
            lockedSnapshot(true, ScmPurchaseLockReasonEnum.MANUAL.name(), now)));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO unlock(PurchaseOrderLockForm form) {
        PurchaseOrderEntity order = lockForWrite(form);
        if (!Boolean.TRUE.equals(order.getLockedFlag())) {
            throw new ScmBusinessException(PURCHASE_ORDER_STATE_INVALID);
        }
        PurchaseOrderValidator.reason(form.getReason(), PURCHASE_UNLOCK_REASON_REQUIRED);
        if (purchaseOrderDao.unlock(order.getId(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(PURCHASE_ORDER_STATE_INVALID);
        }
        PurchaseOrderVO result = purchaseQueryService.orderDetailForCommand(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(ScmPurchaseOperationTypeEnum.PRICE_UNLOCK,
            order.getId(), null, form.getReason(), lockedSnapshot(true, order.getLockReason(), order.getLockedAt()),
            lockedSnapshot(false, null, null)));
        return result;
    }

    /**
     * 自动锁单一条采购单，返回是否真的锁上。
     *
     * <p>
     * 逐单独立事务：一条单被并发修改不应拖住整批。条件更新自带 {@code locked_flag = FALSE}， 手动锁与自动锁同时到达也只有一笔成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean autoLock(Long purchaseOrderId) {
        OffsetDateTime now = OffsetDateTime.now();
        if (purchaseOrderDao.lockIfUnlocked(purchaseOrderId, now, AUTO_LOCK_OPERATOR,
            ScmPurchaseLockReasonEnum.AUTO_7_DAYS.name(), null) != 1) {
            return false;
        }
        PurchaseOperationLogEntity log = new PurchaseOperationLogEntity();
        log.setOperationType(ScmPurchaseOperationTypeEnum.PRICE_AUTO_LOCK.name());
        log.setPurchaseOrderId(purchaseOrderId);
        log.setOperator(AUTO_LOCK_OPERATOR);
        log.setReason("提交满 " + PRICE_WINDOW_DAYS + " 天自动锁单");
        log.setBeforeData(lockedSnapshot(false, null, null));
        log.setAfterData(lockedSnapshot(true, ScmPurchaseLockReasonEnum.AUTO_7_DAYS.name(), now));
        log.setCreatedBy(AUTO_LOCK_OPERATOR);
        purchaseOperationLogDao.append(log);
        return true;
    }

    /**
     * 写路径取单：可见性（采购员范围）+ 版本号。命令侧不靠列表是否显示来收口。
     */
    private PurchaseOrderEntity lockForWrite(PurchaseOrderLockForm form) {
        PurchaseOrderEntity order = purchaseOrderDao.lock(form.getId());
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        purchaseOwnerResolver.requireVisible(order.getPurchaserId());
        if (!Objects.equals(order.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return order;
    }

    private static Map<String, Object> lockedSnapshot(boolean locked, String lockReason, OffsetDateTime lockedAt) {
        Map<String, Object> snapshot = PurchaseSnapshotFactory.snapshot();
        snapshot.put("lockedFlag", locked);
        snapshot.put("lockReason", lockReason);
        snapshot.put("lockedAt", lockedAt);
        return snapshot;
    }
}
