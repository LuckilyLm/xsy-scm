package net.lab1024.sa.admin.module.scm.purchase.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseConfigKey;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOperationLogDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderItemDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseReceiptDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseReceiptItemDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.ReceiptWeighingRecordDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseReceiptEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseReceiptItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.ReceiptWeighingRecordEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptBatchDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderStateMachine;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderValidator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseReceiptQuantityCalculator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import net.lab1024.sa.base.module.support.config.ConfigService;
import net.lab1024.sa.base.module.support.config.domain.ConfigVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_EMPTY;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_DELETE_STATE_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_ITEM_INCOMPLETE;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_ORDER_STATE_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_OVER_RECEIVED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_STATE_INVALID;

/**
 * 采购收货命令服务（W5 Target Design §4.3 / §7.5 / §7.9）。
 *
 * <p><b>收货单只有 2 个状态</b>（Q7/Q7a）：`DRAFT` 不产生任何副作用，`CONFIRMED` 只读。
 * 「多次收货」由「一采购单多收货单」表达，不是同一张单反复确认（修 A-D5）。
 *
 * <p><b>容差走 SmartAdmin 原生 Config（Q3a）</b>：{@link ConfigService#getConfig(String)}
 * 读 `t_config`，缺失 → 回退 10，非法 → 40999。SCM **不自建缓存、不建配置表**
 * （`ConfigService` 自带 `ConcurrentHashMap` + `@SmartReload(CONFIG_RELOAD)`）。
 *
 * <p><b>标品 / 非标品（P21/P22）</b>：标品的有效数量 = 本次申报数量，且实重三字段必须全空；
 * 非标品的有效数量 = 本次实重（必填 &gt; 0，来源必须 `MANUAL`）。
 * **`planned_quantity` 永不被覆盖**。
 *
 * <p><b>锁序（§7.9 对 `receipt.confirm` 明列的次序）</b>：
 * 采购单 → 收货单 → 采购行（按 id 升序）→ 收货行（按 id 升序）。
 * 全库只有本类同时持有「收货单」与「采购行」两把锁，因此与 `order.create`
 * （需求 → 采购单 → 采购行）不构成环。
 *
 * <p><b>W5 零库存</b>：`confirm` 的最后一步（§4.3 第 15 步）在 W6 才调用
 * `PurchaseInventoryContract.postInbound(...)`；W5 **没有调用点**（G-04）。
 */
@Service
@RequiredArgsConstructor
public class PurchaseReceiptService {

    private final PurchaseReceiptDao purchaseReceiptDao;

    private final PurchaseReceiptItemDao purchaseReceiptItemDao;

    private final ReceiptWeighingRecordDao receiptWeighingRecordDao;

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseOrderItemDao purchaseOrderItemDao;

    private final PurchaseOperationLogDao purchaseOperationLogDao;

    private final PurchaseNumberGenerator numberGenerator;

    private final PurchaseIdempotencyService idempotencyService;

    private final PurchaseQueryService queryService;

    private final ConfigService configService;

    // ------------------------------------------------------------------
    // receipt.create
    // ------------------------------------------------------------------

    /**
     * 建收货单（`DRAFT`，**不产生任何副作用**）。
     *
     * <p>行由服务端按采购单的**全部活动行**自动生成：不允许调用方挑行，
     * 否则「确认时必须覆盖全部行」这条规则（40998）就失去了基准集合。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptVO create(PurchaseReceiptCreateForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(
                "PURCHASE_RECEIPT_CREATE:" + form.getPurchaseOrderId(), idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseReceiptVO.class);
        }

        PurchaseOrderEntity order = purchaseOrderDao.lock(form.getPurchaseOrderId());
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        if (!PurchaseOrderStateMachine.receivable(order.getStatus())) {
            // RECEIVED / SHORT_CLOSED / CANCELLED / DRAFT 都不允许新收货（T8）
            throw new ScmBusinessException(PURCHASE_RECEIPT_ORDER_STATE_INVALID);
        }
        List<PurchaseOrderItemEntity> orderItems = purchaseOrderItemDao.lockByOrderId(order.getId());
        if (orderItems.isEmpty()) {
            throw new ScmBusinessException(PURCHASE_ORDER_ITEM_EMPTY);
        }

        PurchaseReceiptEntity receipt =
                PurchaseSnapshotFactory.receipt(order, numberGenerator.receipt(), form.getRemark());
        stamp(receipt, true);
        purchaseReceiptDao.insert(receipt);

        List<PurchaseReceiptItemEntity> items = new ArrayList<>(orderItems.size());
        for (int index = 0; index < orderItems.size(); index++) {
            PurchaseReceiptItemEntity item =
                    PurchaseSnapshotFactory.receiptItem(receipt, orderItems.get(index), index);
            stamp(item, true);
            purchaseReceiptItemDao.insert(item);
            items.add(item);
        }

        PurchaseReceiptVO result = queryService.receiptDetail(receipt.getId());
        Map<String, Object> after = PurchaseSnapshotFactory.snapshot();
        after.put("receiptNo", receipt.getReceiptNo());
        after.put("items", items.stream().map(PurchaseReceiptService::receiptItemSnapshot).toList());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.RECEIPT_CREATE, order.getId(), receipt.getId(),
                null, null, after));
        idempotencyService.complete(claim, "PURCHASE_RECEIPT", receipt.getId(), result);
        return result;
    }

    // ------------------------------------------------------------------
    // receipt.update（只改备注）
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptVO update(PurchaseReceiptUpdateForm form) {
        PurchaseReceiptEntity receipt = lockReceipt(form.getId());
        version(receipt.getVersion(), form.getVersion());
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_STATE_INVALID);
        }

        Map<String, Object> before = PurchaseSnapshotFactory.snapshot();
        before.put("remark", receipt.getRemark());
        before.put("version", receipt.getVersion());

        receipt.setRemark(PurchaseOrderValidator.trim(form.getRemark()));
        stamp(receipt, false);
        if (purchaseReceiptDao.updateById(receipt) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        Map<String, Object> after = PurchaseSnapshotFactory.snapshot();
        after.put("remark", receipt.getRemark());
        after.put("version", receipt.getVersion() + 1);
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.RECEIPT_UPDATE, receipt.getPurchaseOrderId(),
                receipt.getId(), null, before, after));
        return queryService.receiptDetail(receipt.getId());
    }

    // ------------------------------------------------------------------
    // receipt.confirm（§4.3 的 15 步）
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptVO confirm(PurchaseReceiptConfirmForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(
                "PURCHASE_RECEIPT_CONFIRM:" + form.getId(), idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseReceiptVO.class);
        }

        // 锁序（§7.9）：采购单 → 收货单 → 采购行 → 收货行。
        // 先无锁读一次只为拿到 purchaseOrderId（随后会被 FOR UPDATE 的读覆盖并重新校验）。
        PurchaseReceiptEntity probe = purchaseReceiptDao.selectById(form.getId());
        if (probe == null) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_NOT_FOUND);
        }
        PurchaseOrderEntity order = purchaseOrderDao.lock(probe.getPurchaseOrderId());
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        PurchaseReceiptEntity receipt = lockReceipt(form.getId());
        version(receipt.getVersion(), form.getVersion());
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_STATE_INVALID);
        }
        if (!PurchaseOrderStateMachine.receivable(order.getStatus())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_ORDER_STATE_INVALID);
        }

        Map<Long, PurchaseOrderItemEntity> orderItems = purchaseOrderItemDao.lockByOrderId(order.getId())
                .stream()
                .collect(Collectors.toMap(PurchaseOrderItemEntity::getId, Function.identity(), (a, b) -> a));
        List<PurchaseReceiptItemEntity> receiptItems = purchaseReceiptItemDao.lockByReceiptId(receipt.getId());
        Map<Long, PurchaseReceiptConfirmForm.Item> requested = requestedLines(form, receiptItems);

        // Q3a：容差来自 SmartAdmin 原生 Config；缺失回退 10，非法 40999
        ConfigVO toleranceConfig = configService.getConfig(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT);
        int tolerance = PurchaseReceiptQuantityCalculator.tolerance(
                toleranceConfig == null ? null : toleranceConfig.getConfigValue());

        List<Map<String, Object>> beforeItems = new ArrayList<>(receiptItems.size());
        List<Map<String, Object>> afterItems = new ArrayList<>(receiptItems.size());

        for (PurchaseReceiptItemEntity line : receiptItems) {
            PurchaseReceiptConfirmForm.Item input = requested.get(line.getId());
            version(line.getVersion(), input.getVersion());

            PurchaseOrderItemEntity orderItem = orderItems.get(line.getPurchaseOrderItemId());
            if (orderItem == null) {
                throw new ScmBusinessException(PURCHASE_ORDER_ITEM_NOT_FOUND);
            }

            BigDecimal declared = PurchaseReceiptQuantityCalculator.declared(input.getReceivedQuantity());
            BigDecimal actualWeight = input.getActualWeight() == null
                    ? null
                    : PurchaseReceiptQuantityCalculator.declared(input.getActualWeight());
            String weighingSource = PurchaseOrderValidator.trim(input.getWeightSource());
            String correctionReason = PurchaseOrderValidator.trim(input.getCorrectionReason());

            // 标品：三字段必须全空；非标品：实重必填 > 0 且来源 MANUAL（40083）
            BigDecimal effective = PurchaseReceiptQuantityCalculator.effectiveQuantity(
                    orderItem.getProductTypeSnapshot(), declared, actualWeight,
                    weighingSource, correctionReason);

            // 本次有效数量 > 可收上限 → 40989，**整笔回滚**（不在行级部分提交）
            BigDecimal ceiling = PurchaseReceiptQuantityCalculator.ceiling(
                    orderItem.getPlannedQuantity(), tolerance);
            BigDecimal available = PurchaseReceiptQuantityCalculator.available(
                    ceiling, orderItem.getReceivedQuantity());
            if (effective.compareTo(available) > 0) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_OVER_RECEIVED);
            }

            BigDecimal cumulative = orderItem.getReceivedQuantity().add(effective);
            Map<String, Object> beforeLine = PurchaseSnapshotFactory.snapshot();
            beforeLine.put("id", line.getId());
            beforeLine.put("receivedQuantity", PurchaseSnapshotFactory.fixed(line.getReceivedQuantity()));
            beforeItems.add(beforeLine);

            // received_quantity 只增不减；累计的唯一入口是 accumulateReceived（持行锁）
            if (purchaseOrderItemDao.accumulateReceived(
                    orderItem.getId(), effective, ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
            // 内存里同步累计值，供「是否收齐」与对账量计算使用
            orderItem.setReceivedQuantity(cumulative);

            BigDecimal remaining = PurchaseReceiptQuantityCalculator.remaining(
                    orderItem.getPlannedQuantity(), cumulative);
            BigDecimal over = PurchaseReceiptQuantityCalculator.overReceipt(
                    orderItem.getPlannedQuantity(), cumulative);
            BigDecimal difference = PurchaseReceiptQuantityCalculator.difference(
                    orderItem.getPlannedQuantity(), cumulative);
            String weightUnit = actualWeight == null ? null : orderItem.getPurchaseUnitSnapshot();

            if (purchaseReceiptItemDao.updateReconciliation(line.getId(), line.getVersion(),
                    effective, cumulative, remaining, over, difference,
                    actualWeight, weightUnit, weighingSource, correctionReason,
                    ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }

            if (actualWeight != null) {
                // 只追加的审计事实（无 version / deleted / updated_*），同 A 源 inventory_movement 纪律
                appendWeighingRecord(line.getId(), actualWeight, weightUnit, correctionReason);
            }

            Map<String, Object> afterLine = PurchaseSnapshotFactory.snapshot();
            afterLine.put("id", line.getId());
            afterLine.put("receivedQuantity", PurchaseSnapshotFactory.fixed(effective));
            afterLine.put("cumulative", PurchaseSnapshotFactory.fixed(cumulative));
            afterLine.put("over", PurchaseSnapshotFactory.fixed(over));
            afterLine.put("difference", PurchaseSnapshotFactory.fixed(difference));
            afterItems.add(afterLine);
        }

        // T7：全部活动行收齐 → RECEIVED，否则 PARTIALLY_RECEIVED
        boolean allFulfilled = orderItems.values().stream().allMatch(item ->
                item.getReceivedQuantity().compareTo(item.getPlannedQuantity()) >= 0);
        String nextStatus = PurchaseOrderStateMachine.afterReceipt(allFulfilled);
        PurchaseOrderStateMachine.transition(order.getStatus(), nextStatus);
        order.setStatus(nextStatus);
        stamp(order, false);
        if (purchaseOrderDao.updateById(order) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now();
        receipt.setStatus("CONFIRMED");
        receipt.setReceivedAt(now);
        receipt.setConfirmedAt(now);
        receipt.setOperator(ScmOperator.current());
        stamp(receipt, false);
        if (purchaseReceiptDao.updateById(receipt) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        Map<String, Object> before = PurchaseSnapshotFactory.snapshot();
        before.put("items", beforeItems);
        Map<String, Object> after = PurchaseSnapshotFactory.snapshot();
        after.put("items", afterItems);
        after.put("orderStatus", order.getStatus());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.RECEIPT_CONFIRM, order.getId(), receipt.getId(),
                null, before, after));
        // §4.3 第 15 步（W6）：PurchaseInventoryContract.postInbound(...) —— W5 零调用点

        PurchaseReceiptVO result = queryService.receiptDetail(receipt.getId());
        idempotencyService.complete(claim, "PURCHASE_RECEIPT", receipt.getId(), result);
        return result;
    }

    // ------------------------------------------------------------------
    // receipt.delete
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public void delete(PurchaseReceiptDeleteForm form) {
        PurchaseReceiptEntity receipt = purchaseReceiptDao.lock(form.getId());
        if (receipt == null) {
            // 幂等：已删除视为成功（同 W4 的 delete 语义）
            return;
        }
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_DELETE_STATE_INVALID);
        }

        PurchaseReceiptVO before = queryService.receiptDetail(receipt.getId());
        purchaseReceiptItemDao.softDeleteByReceiptId(receipt.getId(), ScmOperator.current());
        if (purchaseReceiptDao.softDelete(
                receipt.getId(), receipt.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        Map<String, Object> after = PurchaseSnapshotFactory.snapshot();
        after.put("deleted", true);
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.RECEIPT_DELETE, receipt.getPurchaseOrderId(),
                receipt.getId(), null, receiptSnapshot(before), after));
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(PurchaseReceiptBatchDeleteForm form) {
        // 按 id 升序：批量删除也必须遵守确定性锁序（P12）
        form.getReceipts().stream()
                .sorted(Comparator.comparing(PurchaseReceiptBatchDeleteForm.PurchaseReceiptVersionForm::getId))
                .forEach(row -> delete(deleteForm(row)));
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 请求行集合必须**恰好等于**本收货单的全部活动行集合（修 A-D5/G11）。
     *
     * <p>不允许只提交子集：否则「确认了但仍有 0 数量行」的歧义会一直存在，
     * 而 `received_quantity` 的累计口径也会变得不可推理。
     */
    private static Map<Long, PurchaseReceiptConfirmForm.Item> requestedLines(
            PurchaseReceiptConfirmForm form, List<PurchaseReceiptItemEntity> receiptItems) {
        Map<Long, PurchaseReceiptConfirmForm.Item> requested = new LinkedHashMap<>();
        for (PurchaseReceiptConfirmForm.Item item : form.getItems()) {
            if (requested.put(item.getReceiptItemId(), item) != null) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_ITEM_INCOMPLETE);
            }
        }
        Set<Long> active = receiptItems.stream().map(PurchaseReceiptItemEntity::getId)
                .collect(Collectors.toSet());
        if (active.isEmpty() || !active.equals(requested.keySet())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_ITEM_INCOMPLETE);
        }
        return requested;
    }

    private void appendWeighingRecord(Long receiptItemId, BigDecimal actualWeight,
                                      String weightUnit, String correctionReason) {
        ReceiptWeighingRecordEntity record = new ReceiptWeighingRecordEntity();
        record.setPurchaseReceiptItemId(receiptItemId);
        // G-05（手工录入）：原始读数与确认读数同值；`DEVICE` 是 W6+ 的扩展点（CHECK 目前只允许 MANUAL）
        record.setRawReading(actualWeight);
        record.setConfirmedReading(actualWeight);
        record.setUnit(weightUnit);
        record.setSource("MANUAL");
        record.setModificationReason(correctionReason);
        record.setRecordedAt(OffsetDateTime.now());
        record.setOperator(ScmOperator.current());
        record.setCreatedAt(record.getRecordedAt());
        record.setCreatedBy(record.getOperator());
        receiptWeighingRecordDao.append(record);
    }

    private PurchaseReceiptEntity lockReceipt(Long id) {
        PurchaseReceiptEntity receipt = purchaseReceiptDao.lock(id);
        if (receipt == null) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_NOT_FOUND);
        }
        return receipt;
    }

    private static void version(Integer actual, Integer expected) {
        if (!Objects.equals(actual, expected)) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private static PurchaseReceiptDeleteForm deleteForm(
            PurchaseReceiptBatchDeleteForm.PurchaseReceiptVersionForm row) {
        PurchaseReceiptDeleteForm form = new PurchaseReceiptDeleteForm();
        form.setId(row.getId());
        return form;
    }

    private static void stamp(PurchaseOrderEntity row, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        row.setUpdatedAt(now);
        row.setUpdatedBy(ScmOperator.current());
        if (creating) {
            row.setCreatedAt(now);
            row.setCreatedBy(row.getUpdatedBy());
        }
    }

    private static void stamp(PurchaseReceiptEntity row, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        row.setUpdatedAt(now);
        row.setUpdatedBy(ScmOperator.current());
        if (creating) {
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedAt(now);
            row.setCreatedBy(row.getUpdatedBy());
        }
    }

    private static void stamp(PurchaseReceiptItemEntity row, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        row.setUpdatedAt(now);
        row.setUpdatedBy(ScmOperator.current());
        if (creating) {
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedAt(now);
            row.setCreatedBy(row.getUpdatedBy());
        }
    }

    private static Map<String, Object> receiptItemSnapshot(PurchaseReceiptItemEntity row) {
        Map<String, Object> snapshot = PurchaseSnapshotFactory.snapshot();
        snapshot.put("id", row.getId());
        snapshot.put("purchaseOrderItemId", row.getPurchaseOrderItemId());
        snapshot.put("skuId", row.getSkuId());
        snapshot.put("plannedQuantity", PurchaseSnapshotFactory.fixed(row.getPlannedQuantity()));
        snapshot.put("receivedQuantity", PurchaseSnapshotFactory.fixed(row.getReceivedQuantity()));
        return snapshot;
    }

    /** `RECEIPT_DELETE` 的「全量」前态（§7.12）。 */
    private static Map<String, Object> receiptSnapshot(PurchaseReceiptVO vo) {
        Map<String, Object> snapshot = PurchaseSnapshotFactory.snapshot();
        snapshot.put("id", vo.getId());
        snapshot.put("receiptNo", vo.getReceiptNo());
        snapshot.put("purchaseOrderId", vo.getPurchaseOrderId());
        snapshot.put("status", vo.getStatus());
        snapshot.put("remark", vo.getRemark());
        snapshot.put("version", vo.getVersion());
        List<Map<String, Object>> items = new ArrayList<>();
        if (vo.getItems() != null) {
            for (PurchaseReceiptItemVO item : vo.getItems()) {
                Map<String, Object> one = PurchaseSnapshotFactory.snapshot();
                one.put("id", item.getId());
                one.put("purchaseOrderItemId", item.getPurchaseOrderItemId());
                one.put("receivedQuantity", PurchaseSnapshotFactory.fixed(item.getReceivedQuantity()));
                items.add(one);
            }
        }
        snapshot.put("items", items);
        return snapshot;
    }
}
