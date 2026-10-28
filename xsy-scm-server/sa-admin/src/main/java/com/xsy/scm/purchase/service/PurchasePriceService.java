package com.xsy.scm.purchase.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.purchase.constant.ScmPurchaseIdempotencyResourceTypeEnum;
import com.xsy.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import com.xsy.scm.purchase.constant.ScmPurchasePriceSourceEnum;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.dao.PurchaseOperationLogDao;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.dao.PurchasePriceAdjustDao;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOperationLogEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import com.xsy.scm.purchase.domain.entity.PurchasePriceAdjustEntity;
import com.xsy.scm.purchase.domain.form.PurchasePriceForm;
import com.xsy.scm.purchase.domain.vo.PurchaseLastPriceVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.manager.PurchaseAmountCalculator;
import com.xsy.scm.purchase.manager.PurchaseOrderAuditSnapshotFactory;
import com.xsy.scm.purchase.manager.PurchaseOrderValidator;
import com.xsy.scm.purchase.manager.PurchaseSnapshotFactory;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ITEM_NOT_PRICE_PENDING;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_LOCKED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_PRICE_ADJUST_NOT_ALLOWED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_PRICE_REASON_REQUIRED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_PRICE_WINDOW_CLOSED;

/**
 * 采购价后置：补价、改价与超期自动回填。
 *
 * <p>
 * 边界：
 * <ul>
 * <li>补价只对「待定价且未锁单且窗口未过期」的行生效，是日常路径；</li>
 * <li>改价是带原因、带独立权限的例外路径，可在锁单后使用，但收货一经确认就不再改写库存成本；</li>
 * <li>超期自动回填只使用「最近一次已提交采购价」，没有参考价时保持待定价并记警告，绝不写 0。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchasePriceService {

    /** 自动回填在日志与审计里的系统操作人。 */
    public static final String AUTO_FILL_OPERATOR = "system:purchase-price-auto-fill";

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseOrderItemDao purchaseOrderItemDao;

    private final PurchasePriceAdjustDao purchasePriceAdjustDao;

    private final PurchaseOperationLogDao purchaseOperationLogDao;

    private final PurchaseQueryService purchaseQueryService;

    private final PurchaseOwnerResolver purchaseOwnerResolver;

    private final PurchaseIdempotencyService purchaseIdempotencyService;

    /**
     * 补价：待定价行 → 有价。窗口内、未锁单才允许，日常由采购员操作。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO fill(Long itemId, PurchasePriceForm form, String idempotencyKey) {
        var claim = purchaseIdempotencyService.claim("PURCHASE_PRICE_FILL:" + itemId, idempotencyKey, form);
        if (claim.replay()) {
            return purchaseIdempotencyService.replay(claim, PurchaseOrderVO.class);
        }

        PurchaseOrderItemEntity item = requireItem(itemId);
        PurchaseOrderEntity order = lockOrder(item.getPurchaseOrderId());
        PurchaseOrderItemEntity locked = lockItem(itemId, order.getId());

        if (!Boolean.TRUE.equals(locked.getPricePending())) {
            throw new ScmBusinessException(PURCHASE_ITEM_NOT_PRICE_PENDING);
        }
        if (Boolean.TRUE.equals(order.getLockedFlag())) {
            throw new ScmBusinessException(PURCHASE_ORDER_LOCKED);
        }
        OffsetDateTime now = OffsetDateTime.now();
        assertWindowOpen(locked, now);

        applyPrice(locked, PurchaseOrderValidator.decimal(form.getPrice(), false), now,
            ScmPurchasePriceSourceEnum.MANUAL.name(), ScmOperator.current());
        recordAudit(order, locked, null, locked.getPurchasePrice(), ScmPurchasePriceSourceEnum.MANUAL.name(),
            form.getReason(), ScmOperator.current(), idempotencyKey);

        PurchaseOrderVO result = purchaseQueryService.orderDetailForCommand(order.getId());
        purchaseOperationLogDao
            .append(PurchaseSnapshotFactory.operationLog(ScmPurchaseOperationTypeEnum.PRICE_FILL, order.getId(), null,
                form.getReason(), priceSnapshot(null, true), priceSnapshot(locked.getPurchasePrice(), false)));
        purchaseIdempotencyService.complete(claim, ScmPurchaseIdempotencyResourceTypeEnum.PURCHASE_ORDER.name(),
            order.getId(), result);
        return result;
    }

    /**
     * 改价：已定价行调整价格，或在锁单 / 过期后由有权限的人补上真实价。
     *
     * <p>
     * 收货确认后库存成本已按当时价格入账，这里直接拒绝，不做成本回冲（另立需求）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO adjust(Long itemId, PurchasePriceForm form, String idempotencyKey) {
        var claim = purchaseIdempotencyService.claim("PURCHASE_PRICE_ADJUST:" + itemId, idempotencyKey, form);
        if (claim.replay()) {
            return purchaseIdempotencyService.replay(claim, PurchaseOrderVO.class);
        }

        PurchaseOrderItemEntity item = requireItem(itemId);
        PurchaseOrderEntity order = lockOrder(item.getPurchaseOrderId());
        PurchaseOrderItemEntity locked = lockItem(itemId, order.getId());

        if (locked.getReceivedQuantity() != null && locked.getReceivedQuantity().signum() > 0) {
            throw new ScmBusinessException(PURCHASE_PRICE_ADJUST_NOT_ALLOWED);
        }
        PurchaseOrderValidator.reason(form.getReason(), PURCHASE_PRICE_REASON_REQUIRED);

        BigDecimal oldPrice = locked.getPurchasePrice();
        boolean wasPending = Boolean.TRUE.equals(locked.getPricePending());
        OffsetDateTime now = OffsetDateTime.now();
        applyPrice(locked, PurchaseOrderValidator.decimal(form.getPrice(), false), now,
            ScmPurchasePriceSourceEnum.MANUAL.name(), ScmOperator.current());
        recordAudit(order, locked, oldPrice, locked.getPurchasePrice(), ScmPurchasePriceSourceEnum.MANUAL.name(),
            form.getReason(), ScmOperator.current(), idempotencyKey);

        PurchaseOrderVO result = purchaseQueryService.orderDetailForCommand(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(ScmPurchaseOperationTypeEnum.PRICE_ADJUST,
            order.getId(), null, form.getReason(), priceSnapshot(oldPrice, wasPending),
            priceSnapshot(locked.getPurchasePrice(), false)));
        purchaseIdempotencyService.complete(claim, ScmPurchaseIdempotencyResourceTypeEnum.PURCHASE_ORDER.name(),
            order.getId(), result);
        return result;
    }

    /**
     * 超期自动回填一张采购单，返回是否有行被回填。
     *
     * <p>
     * 逐单独立事务、逐行使用最近已提交价；没有参考价的行保持待定价并记警告 —— 写 0 会把「不知道」伪装成「免费」。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean autoFill(Long purchaseOrderId) {
        PurchaseOrderEntity order = purchaseOrderDao.lock(purchaseOrderId);
        if (order == null || Boolean.TRUE.equals(order.getLockedFlag())) {
            return false;
        }
        List<PurchaseOrderItemEntity> rows = purchaseOrderItemDao.lockByOrderId(purchaseOrderId);
        List<PurchaseOrderItemEntity> pending = rows.stream().filter(row -> Boolean.TRUE.equals(row.getPricePending()))
            .filter(row -> row.getPriceWindowStartedAt() != null).toList();
        if (pending.isEmpty()) {
            return false;
        }

        // 参考价必须同单位：同一规格可能先按公斤、后按斤采购，跨单位套用会把单价填错。
        Map<String,
            PurchaseLastPriceVO> references = purchaseOrderItemDao
                .latestPurchasePricesByUnit(pending.stream().map(PurchaseOrderItemEntity::getSkuId).distinct().toList(),
                    ScmPurchaseStatusEnum.committedNames(), ScmValueScope.all())
                .stream()
                .collect(Collectors.toMap(reference -> unitKey(reference.getSkuId(), reference.getPurchaseUnit()),
                    Function.identity(), (a, b) -> a));

        boolean filled = false;
        OffsetDateTime now = OffsetDateTime.now();
        for (PurchaseOrderItemEntity row : pending) {
            if (now.isBefore(row.getPriceWindowStartedAt().plusDays(PurchaseOrderLockService.PRICE_WINDOW_DAYS))) {
                continue;
            }
            PurchaseLastPriceVO reference = references.get(unitKey(row.getSkuId(), row.getPurchaseUnitSnapshot()));
            if (reference == null || reference.getPurchasePrice() == null) {
                // 保持待定价，等人工补价；自动锁单不会让这一行变成 0 元
                log.warn("采购价超期但没有同单位的最近采购价，保持待定价：orderId={}, itemId={}, skuId={}, unit={}", order.getId(), row.getId(),
                    row.getSkuId(), row.getPurchaseUnitSnapshot());
                continue;
            }
            applyPrice(row, reference.getPurchasePrice(), now, ScmPurchasePriceSourceEnum.AUTO_LAST_PRICE.name(),
                AUTO_FILL_OPERATOR);
            recordAudit(order, row, null, row.getPurchasePrice(), ScmPurchasePriceSourceEnum.AUTO_LAST_PRICE.name(),
                "提交满 " + PurchaseOrderLockService.PRICE_WINDOW_DAYS + " 天，按最近已提交采购价回填", AUTO_FILL_OPERATOR, null);
            filled = true;
        }
        if (!filled) {
            return false;
        }

        PurchaseOrderVO result = purchaseQueryService.orderDetailForCommand(order.getId());
        // 系统任务没有登录上下文，不能用会读取当前登录人的工厂方法，操作人显式写成系统账号
        PurchaseOperationLogEntity operationLog = new PurchaseOperationLogEntity();
        operationLog.setOperationType(ScmPurchaseOperationTypeEnum.PRICE_AUTO_FILL.name());
        operationLog.setPurchaseOrderId(order.getId());
        operationLog.setOperator(AUTO_FILL_OPERATOR);
        operationLog.setReason("提交满 " + PurchaseOrderLockService.PRICE_WINDOW_DAYS + " 天，按最近已提交采购价回填");
        operationLog.setAfterData(PurchaseOrderAuditSnapshotFactory.orderAuditSnapshot(result));
        operationLog.setCreatedBy(AUTO_FILL_OPERATOR);
        purchaseOperationLogDao.append(operationLog);
        return true;
    }

    /**
     * 写入价格、行金额并清待定价。行金额一律按「计划量 × 新价」重算，不依赖旧行金额。
     */
    private void applyPrice(PurchaseOrderItemEntity item, BigDecimal price, OffsetDateTime now, String source,
        String operator) {
        item.setPurchasePrice(price);
        item.setPricePending(false);
        item.setPriceFilledAt(now);
        item.setPriceFillSource(source);
        item.setLineAmount(PurchaseAmountCalculator.lineAmount(item.getPlannedQuantity(), price));
        // 不用 PurchaseEntityStamper：它读取当前登录人，系统任务里没有登录上下文
        item.setUpdatedAt(now);
        item.setUpdatedBy(operator);
        if (purchaseOrderItemDao.updateById(item) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        // 单头合计必须跟着行金额走，否则页面出现「行有价、合计还是 0」
        if (purchaseOrderDao.refreshTotalAmount(item.getPurchaseOrderId()) != 1) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
    }

    private void recordAudit(PurchaseOrderEntity order, PurchaseOrderItemEntity item, BigDecimal oldPrice,
        BigDecimal newPrice, String source, String reason, String operator, String idempotencyKey) {
        PurchasePriceAdjustEntity audit = new PurchasePriceAdjustEntity();
        audit.setPurchaseOrderId(order.getId());
        audit.setPurchaseOrderItemId(item.getId());
        audit.setSkuId(item.getSkuId());
        audit.setOldPrice(oldPrice);
        audit.setNewPrice(newPrice);
        audit.setSource(source);
        audit.setReason(PurchaseOrderValidator.trim(reason));
        audit.setOperator(operator);
        audit.setIdempotencyKey(PurchaseOrderValidator.trim(idempotencyKey));
        audit.setCreatedAt(OffsetDateTime.now());
        purchasePriceAdjustDao.insert(audit);
    }

    /** 参考价按「商品规格 + 采购单位」配对，单位不同的历史价不能被当成同一口径。 */
    private static String unitKey(Long skuId, String purchaseUnit) {
        return skuId + "|" + (purchaseUnit == null ? "" : purchaseUnit);
    }

    private static void assertWindowOpen(PurchaseOrderItemEntity item, OffsetDateTime now) {
        OffsetDateTime startedAt = item.getPriceWindowStartedAt();
        if (startedAt != null && !now.isBefore(startedAt.plusDays(PurchaseOrderLockService.PRICE_WINDOW_DAYS))) {
            throw new ScmBusinessException(PURCHASE_PRICE_WINDOW_CLOSED);
        }
    }

    private PurchaseOrderItemEntity requireItem(Long itemId) {
        PurchaseOrderItemEntity item = purchaseOrderItemDao.selectById(itemId);
        if (item == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_ITEM_NOT_FOUND);
        }
        return item;
    }

    private PurchaseOrderEntity lockOrder(Long orderId) {
        PurchaseOrderEntity order = purchaseOrderDao.lock(orderId);
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        purchaseOwnerResolver.requireVisible(order.getPurchaserId());
        return order;
    }

    /** 锁序与收货确认一致：采购单 → 采购行；行可能已被并发删掉或换单，必须重新校验归属。 */
    private PurchaseOrderItemEntity lockItem(Long itemId, Long expectedOrderId) {
        PurchaseOrderItemEntity item = purchaseOrderItemDao.lock(itemId);
        if (item == null || !Objects.equals(item.getPurchaseOrderId(), expectedOrderId)) {
            throw new ScmBusinessException(PURCHASE_ORDER_ITEM_NOT_FOUND);
        }
        return item;
    }

    private static Map<String, Object> priceSnapshot(BigDecimal price, boolean pending) {
        Map<String, Object> snapshot = PurchaseSnapshotFactory.snapshot();
        snapshot.put("purchasePrice", PurchaseSnapshotFactory.fixed(price));
        snapshot.put("pricePending", pending);
        return snapshot;
    }
}
