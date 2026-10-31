package com.xsy.scm.purchase.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.finance.service.FinancePayableService;
import com.xsy.scm.purchase.constant.PurchaseConfigKey;
import com.xsy.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;
import com.xsy.scm.purchase.constant.ScmPutawayStatusEnum;
import com.xsy.scm.purchase.constant.ScmReceiptStatusEnum;
import com.xsy.scm.purchase.constant.ScmWeighingSourceEnum;
import com.xsy.scm.finance.constant.ScmFinancePayableSourceTypeEnum;
import com.xsy.scm.purchase.dao.PurchaseOperationLogDao;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseAccountDao;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.dao.PurchaseReceiptDao;
import com.xsy.scm.purchase.dao.PurchaseReceiptItemDao;
import com.xsy.scm.purchase.dao.ReceiptWeighingRecordDao;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseReceiptEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseReceiptItemEntity;
import com.xsy.scm.purchase.domain.entity.ReceiptWeighingRecordEntity;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptBatchDeleteForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptDeleteForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptUpdateForm;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.trace.domain.vo.TraceLotVO;
import com.xsy.scm.trace.support.TraceLotContract;
import com.xsy.scm.warehouse.service.WarehouseService;
import com.xsy.scm.purchase.manager.PurchaseOrderStateMachine;
import com.xsy.scm.purchase.manager.PurchaseOrderValidator;
import com.xsy.scm.purchase.manager.PurchaseReceiptPutawayGuard;
import com.xsy.scm.purchase.manager.PurchaseReceiptQuantityCalculator;
import com.xsy.scm.purchase.manager.PurchaseSnapshotFactory;
import com.xsy.scm.purchase.support.PurchaseInventoryContract;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;
import com.xsy.scm.purchase.support.PurchaseWarehouseReferenceGuard;
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

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_EMPTY;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_PRICE_PENDING;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_DELETE_STATE_INVALID;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_ITEM_INCOMPLETE;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_NOT_FOUND;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_LOT_NOT_TRACKED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_LOT_QUANTITY_MISMATCH;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_LOT_REQUIRED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_ORDER_STATE_INVALID;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_OVER_RECEIVED;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_PUTAWAY_STATE_INVALID;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_STATE_INVALID;
import static com.xsy.scm.purchase.service.PurchaseReceiptSnapshotMapper.receiptSnapshot;

/**
 * 处理采购收货。收货单确认后不能再改；分次收货要创建多张收货单。标品按申报数量入库，非标品按实际重量入库。 锁顺序为采购单、收货单、采购明细、收货明细；明细按 ID 升序。确认时在同一事务内登记入库和应付； 超收容差读取系统配置，默认
 * 10%。
 */
@Service
@RequiredArgsConstructor
public class PurchaseReceiptService {

    private final PurchaseReceiptDao purchaseReceiptDao;

    private final PurchaseReceiptItemDao purchaseReceiptItemDao;

    private final ReceiptWeighingRecordDao receiptWeighingRecordDao;

    private final PurchaseOrderDao purchaseOrderDao;
    private final PurchaseAccountDao purchaseAccountDao;

    private final PurchaseOrderItemDao purchaseOrderItemDao;

    private final PurchaseOperationLogDao purchaseOperationLogDao;

    private final PurchaseNumberGenerator numberGenerator;

    private final PurchaseIdempotencyService purchaseIdempotencyService;

    private final PurchaseQueryService purchaseQueryService;

    private final ConfigService configService;

    /**
     * 库存契约（接线）。运行时注入的是 {@code inventory.support.PurchaseInventoryContractImpl}；期间容器里没有该类型的 Bean，本字段是新增的唯一依赖。
     */
    private final PurchaseInventoryContract purchaseInventoryContract;

    /**
     * 批次档案归追溯域：采购只描述本行分到几个批次、各多少量。
     */
    private final TraceLotContract traceLotContract;

    private final WarehouseService warehouseService;

    /**
     * 仓库引用守卫：收货单创建 / 入库确认前断言仓库仍启用（40987）。
     */
    private final PurchaseWarehouseReferenceGuard purchaseWarehouseReferenceGuard;

    /**
     * 仓库维度的写侧守卫：{@code DIRECT} 收货确认与 {@code WAREHOUSE_CONFIRM} 的上架都会写
     * {@code PURCHASE_IN}，因此两条路径都要判仓库授权；不写库存的收货单编辑/删除不判。
     */
    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    /**
     * 采购归属维度的写侧守卫：收货单挂在采购单上，父单归属不在调用者范围内即拒绝。与 {@link #warehouseScopeGuard} 是两条独立边界，DIRECT 确认要求同时成立。
     */
    private final PurchaseOwnerResolver purchaseOwnerResolver;

    /**
     * 应付生成器：收货确认在同一事务内派生正常应付。生成失败即整笔收货确认回滚 —— 财务侧不接「业务已确认但账上什么都没有」这个缺口。
     */
    private final FinancePayableService financePayableService;

    /**
     * 建收货单（{@code DRAFT}，不产生任何副作用）。
     *
     * <p>
     * 行由服务端按采购单的全部活动行自动生成：不允许调用方挑行，否则「确认时必须覆盖全部行」（40998） 就失去了基准集合。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptVO create(PurchaseReceiptCreateForm form, String idempotencyKey) {
        var claim = purchaseIdempotencyService.claim("PURCHASE_RECEIPT_CREATE:" + form.getPurchaseOrderId(),
            idempotencyKey, form);
        if (claim.replay()) {
            PurchaseOrderEntity order = purchaseOrderDao.selectById(form.getPurchaseOrderId());
            if (order == null) {
                throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
            }
            purchaseOwnerResolver.requireVisible(order.getPurchaserId());
            return purchaseIdempotencyService.replay(claim, PurchaseReceiptVO.class);
        }

        PurchaseOrderEntity order = purchaseOrderDao.lock(form.getPurchaseOrderId());
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        purchaseOwnerResolver.requireVisible(order.getPurchaserId());
        if (purchaseAccountDao.accountId(order.getId()) != null
            || !PurchaseOrderStateMachine.receivable(order.getStatus())) {
            // RECEIVED / SHORT_CLOSED / CANCELLED / DRAFT 都不允许新收货
            throw new ScmBusinessException(PURCHASE_RECEIPT_ORDER_STATE_INVALID);
        }
        // 仓库可能在采购单创建后被停用：收货单创建是「新引用」，必须重查启用态。
        purchaseWarehouseReferenceGuard.requireEnabled(order.getWarehouseId());
        List<PurchaseOrderItemEntity> orderItems = purchaseOrderItemDao.lockByOrderId(order.getId());
        if (orderItems.isEmpty()) {
            throw new ScmBusinessException(PURCHASE_ORDER_ITEM_EMPTY);
        }

        PurchaseReceiptEntity receipt = PurchaseSnapshotFactory.receipt(order, numberGenerator.receipt(),
            form.getRemark());
        // 入库方式由调用方显式二选一（无默认）；DRAFT 期入库状态恒 PENDING。
        receipt.setReceiptMode(form.getReceiptMode());
        receipt.setPutawayStatus(ScmPutawayStatusEnum.PENDING.name());
        stamp(receipt, true);
        purchaseReceiptDao.insert(receipt);

        List<PurchaseReceiptItemEntity> items = new ArrayList<>(orderItems.size());
        for (int index = 0; index < orderItems.size(); index++) {
            PurchaseReceiptItemEntity item = PurchaseSnapshotFactory.receiptItem(receipt, orderItems.get(index), index);
            stamp(item, true);
            purchaseReceiptItemDao.insert(item);
            items.add(item);
        }

        PurchaseReceiptVO result = purchaseQueryService.receiptDetailForCommand(receipt.getId());
        Map<String, Object> after = PurchaseSnapshotFactory.snapshot();
        after.put("receiptNo", receipt.getReceiptNo());
        after.put("items", items.stream().map(PurchaseReceiptSnapshotMapper::receiptItemSnapshot).toList());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(ScmPurchaseOperationTypeEnum.RECEIPT_CREATE,
            order.getId(), receipt.getId(), null, null, after));
        purchaseIdempotencyService.complete(claim, ScmFinancePayableSourceTypeEnum.PURCHASE_RECEIPT.name(),
            receipt.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptVO update(PurchaseReceiptUpdateForm form) {
        PurchaseReceiptEntity receipt = lockReceipt(form.getId());
        purchaseOwnerResolver.requireVisible(orderPurchaserId(receipt));
        version(receipt.getVersion(), form.getVersion());
        if (!ScmReceiptStatusEnum.DRAFT.name().equals(receipt.getStatus())) {
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
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(ScmPurchaseOperationTypeEnum.RECEIPT_UPDATE,
            receipt.getPurchaseOrderId(), receipt.getId(), null, before, after));
        return purchaseQueryService.receiptDetailForCommand(receipt.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptVO confirm(PurchaseReceiptConfirmForm form, String idempotencyKey) {
        var claim = purchaseIdempotencyService.claim("PURCHASE_RECEIPT_CONFIRM:" + form.getId(), idempotencyKey, form);
        if (claim.replay()) {
            PurchaseReceiptEntity receipt = purchaseReceiptDao.selectById(form.getId());
            if (receipt == null) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_NOT_FOUND);
            }
            purchaseOwnerResolver.requireVisible(orderPurchaserId(receipt));
            if (ScmReceiptModeEnum.DIRECT.name().equals(receipt.getReceiptMode())) {
                warehouseScopeGuard.require(receipt.getWarehouseId());
            }
            return purchaseIdempotencyService.replay(claim, PurchaseReceiptVO.class);
        }

        // 锁序：采购单 → 收货单 → 采购行 → 收货行。
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
        // 入库方式决定确认收货时是否同时写入库存。
        boolean direct = ScmReceiptModeEnum.DIRECT.name().equals(receipt.getReceiptMode());
        // 两条边界取交集：采购范围回答「这张采购单归不归他操作」，
        // 仓库范围回答「货允许不允许落进这个仓」，前者不能替代后者 —— 否则握着采购按钮的人可以往
        // 自己无权管理的仓库里写 PURCHASE_IN。WAREHOUSE_CONFIRM 在 confirm 时不写库存，
        // 因此仓库维度由后续的 putaway 判，不在这里提前收权。
        purchaseOwnerResolver.requireVisible(order.getPurchaserId());
        if (direct) {
            warehouseScopeGuard.require(receipt.getWarehouseId());
        }
        version(receipt.getVersion(), form.getVersion());
        if (!ScmReceiptStatusEnum.DRAFT.name().equals(receipt.getStatus())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_STATE_INVALID);
        }
        if (purchaseAccountDao.accountId(order.getId()) != null
            || !PurchaseOrderStateMachine.receivable(order.getStatus())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_ORDER_STATE_INVALID);
        }

        Map<Long, PurchaseOrderItemEntity> orderItems = purchaseOrderItemDao.lockByOrderId(order.getId()).stream()
            .collect(Collectors.toMap(PurchaseOrderItemEntity::getId, Function.identity(), (a, b) -> a));
        // 收货确认是库存成本入账的时点：任何一行还没价就停在这里。允许无价入库会把成本记成 0，
        // 之后再改价也无法回冲——已确认收货的单不再改库存成本，价格差异另立成本更正需求。
        if (orderItems.values().stream().anyMatch(item -> Boolean.TRUE.equals(item.getPricePending()))) {
            throw new ScmBusinessException(PURCHASE_PRICE_PENDING);
        }
        List<PurchaseReceiptItemEntity> receiptItems = purchaseReceiptItemDao.lockByReceiptId(receipt.getId());
        Map<Long, PurchaseReceiptConfirmForm.Item> requested = requestedLines(form, receiptItems);

        // 容差来自 SmartAdmin 原生 Config；缺失回退 10，非法 40999
        ConfigVO toleranceConfig = configService.getConfig(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT);
        int tolerance = PurchaseReceiptQuantityCalculator
            .tolerance(toleranceConfig == null ? null : toleranceConfig.getConfigValue());

        List<Map<String, Object>> beforeItems = new ArrayList<>(receiptItems.size());
        List<Map<String, Object>> afterItems = new ArrayList<>(receiptItems.size());
        // 本行入库数据的最小元组（行 / 采购行 / 有效数量）。循环内只收集，不调用库存接口：
        // 数据装配必须发生在收货单保存为 CONFIRMED 之后（occurredAt/operator 取自那一刻的记录）。
        List<InboundLine> inboundLines = new ArrayList<>(receiptItems.size());
        // 批次追踪按仓库开关：开启后每行必须登记批次，数量之和必须等于本次收货量。
        boolean lotTracking = Boolean.TRUE
            .equals(warehouseService.require(receipt.getWarehouseId()).getLotTrackingEnabled());

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
                orderItem.getProductTypeSnapshot(), declared, actualWeight, weighingSource, correctionReason);

            // 本次有效数量 > 可收上限 → 40989，整笔回滚（不做行级部分提交）
            BigDecimal ceiling = PurchaseReceiptQuantityCalculator.ceiling(orderItem.getPlannedQuantity(), tolerance);
            BigDecimal available = PurchaseReceiptQuantityCalculator.available(ceiling,
                orderItem.getReceivedQuantity());
            if (effective.compareTo(available) > 0) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_OVER_RECEIVED);
            }

            BigDecimal cumulative = orderItem.getReceivedQuantity().add(effective);
            Map<String, Object> beforeLine = PurchaseSnapshotFactory.snapshot();
            beforeLine.put("id", line.getId());
            beforeLine.put("receivedQuantity", PurchaseSnapshotFactory.fixed(line.getReceivedQuantity()));
            beforeItems.add(beforeLine);

            // received_quantity 只增不减；累计的唯一入口是 accumulateReceived（持行锁）
            if (purchaseOrderItemDao.accumulateReceived(orderItem.getId(), effective, ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
            // 内存里同步累计值，供「是否收齐」与对账量计算使用
            orderItem.setReceivedQuantity(cumulative);

            BigDecimal remaining = PurchaseReceiptQuantityCalculator.remaining(orderItem.getPlannedQuantity(),
                cumulative);
            BigDecimal over = PurchaseReceiptQuantityCalculator.overReceipt(orderItem.getPlannedQuantity(), cumulative);
            BigDecimal difference = PurchaseReceiptQuantityCalculator.difference(orderItem.getPlannedQuantity(),
                cumulative);
            String weightUnit = actualWeight == null ? null : orderItem.getPurchaseUnitSnapshot();

            if (purchaseReceiptItemDao.updateReconciliation(line.getId(), line.getVersion(), effective, cumulative,
                remaining, over, difference, actualWeight, weightUnit, weighingSource, correctionReason,
                ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }

            if (actualWeight != null) {
                // 只追加的审计记录（无 version / deleted / updated_*），与 {@code xsy_inventory_movement} 的规则一致
                appendWeighingRecord(line.getId(), actualWeight, weightUnit, correctionReason);
            }

            Map<String, Object> afterLine = PurchaseSnapshotFactory.snapshot();
            afterLine.put("id", line.getId());
            afterLine.put("receivedQuantity", PurchaseSnapshotFactory.fixed(effective));
            afterLine.put("cumulative", PurchaseSnapshotFactory.fixed(cumulative));
            afterLine.put("over", PurchaseSnapshotFactory.fixed(over));
            afterLine.put("difference", PurchaseSnapshotFactory.fixed(difference));
            afterItems.add(afterLine);

            // 收集入库数据元组（不在此处调用库存接口，见 inboundLines 声明处的说明）
            List<TraceLotContract.LotAllocation> allocations = resolveLots(lotTracking, order, line, input, effective,
                ScmOperator.current());
            inboundLines.add(new InboundLine(line, orderItem, effective, allocations));
        }

        // 全部活动行收齐 → RECEIVED，否则 PARTIALLY_RECEIVED
        boolean allFulfilled = orderItems.values().stream()
            .allMatch(item -> item.getReceivedQuantity().compareTo(item.getPlannedQuantity()) >= 0);
        String nextStatus = PurchaseOrderStateMachine.afterReceipt(allFulfilled);
        PurchaseOrderStateMachine.transition(order.getStatus(), nextStatus);
        order.setStatus(nextStatus);
        stamp(order, false);
        if (purchaseOrderDao.updateById(order) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now();
        receipt.setStatus(ScmReceiptStatusEnum.CONFIRMED.name());
        receipt.setReceivedAt(now);
        receipt.setConfirmedAt(now);
        receipt.setOperator(ScmOperator.current());
        if (direct) {
            // DIRECT：确认即物理入库，putaway 与 confirm 同事务完成。
            receipt.setPutawayStatus(ScmPutawayStatusEnum.COMPLETED.name());
            receipt.setPutawayAt(now);
            receipt.setPutawayBy(receipt.getOperator());
        }
        // WAREHOUSE_CONFIRM：此处不写库存，putaway_status 保持 PENDING，等待仓库二次确认。
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
            ScmPurchaseOperationTypeEnum.RECEIPT_CONFIRM, order.getId(), receipt.getId(), null, before, after));
        // 库存入库 —— 与采购侧写入同事务。
        // 位置固定：操作日志之后、幂等 complete 之前。幂等 complete 落在库存写入之后，
        // 保证重放返回的结果就是库存已写入的成功结果。
        if (direct) {
            postInbound(order, receipt, inboundLines, receipt.getConfirmedAt(), receipt.getOperator());
        }

        // 应付时点：企业确认收到货即形成供应商债务，仓库何时 putaway 不决定应付时点，
        // 因此 DIRECT 与 WAREHOUSE_CONFIRM 两条路径都只在这里生成一次，putaway 不再调用。
        // 位置在库存写入之后：财务只消费已经成立的收货记录，自身不锁业务表也不锁余额，
        // 并发的重复触发由 finance_payable 的来源唯一索引仲裁。抛错即整笔 confirm 回滚。
        financePayableService.generateOnReceiptConfirm(receipt.getId());

        PurchaseReceiptVO result = purchaseQueryService.receiptDetailForCommand(receipt.getId());
        purchaseIdempotencyService.complete(claim, ScmFinancePayableSourceTypeEnum.PURCHASE_RECEIPT.name(),
            receipt.getId(), result);
        return result;
    }

    /**
     * 仅已确认且待入库的仓库二次确认收货单可以入库。先锁收货单，再按顺序锁余额；重复确认应拒绝或返回同一幂等结果， 不能二次生成入库流水。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptVO putaway(PurchaseReceiptPutawayForm form, String idempotencyKey) {
        var claim = purchaseIdempotencyService.claim("PURCHASE_RECEIPT_PUTAWAY:" + form.getId(), idempotencyKey, form);
        if (claim.replay()) {
            PurchaseReceiptEntity receipt = purchaseReceiptDao.selectById(form.getId());
            if (receipt == null) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_NOT_FOUND);
            }
            warehouseScopeGuard.require(receipt.getWarehouseId());
            return purchaseIdempotencyService.replay(claim, PurchaseReceiptVO.class);
        }

        PurchaseReceiptEntity receipt = lockReceipt(form.getId());
        // 上架是唯一写 PURCHASE_IN 的采购动作，因此只有它要判仓库授权：判据取收货行上的仓库，
        // 不取请求参数（表单也没有仓库字段）。幂等认领已插入，但随本事务一起回滚，不留痕迹。
        warehouseScopeGuard.require(receipt.getWarehouseId());
        version(receipt.getVersion(), form.getVersion());
        if (!PurchaseReceiptPutawayGuard.putawayAllowed(receipt.getStatus(), receipt.getReceiptMode(),
            receipt.getPutawayStatus())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_PUTAWAY_STATE_INVALID);
        }

        PurchaseOrderEntity order = purchaseOrderDao.selectById(receipt.getPurchaseOrderId());
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        // 已确认收货单的采购单不可能仍是 DRAFT，行价格只读即可（不入行锁）。
        Map<Long, PurchaseOrderItemEntity> orderItems = purchaseOrderItemDao.listByOrderId(order.getId()).stream()
            .collect(Collectors.toMap(PurchaseOrderItemEntity::getId, Function.identity(), (a, b) -> a));
        List<PurchaseReceiptItemEntity> receiptItems = purchaseReceiptItemDao.listByReceiptId(receipt.getId());

        // 入库数据的最小元组：数量取确认时已保存的 received_quantity，单位/单价取采购行记录。
        Map<Long, List<TraceLotContract.LotAllocation>> lotsByItem = lotsByReceiptItem(receiptItems);
        List<InboundLine> inboundLines = new ArrayList<>(receiptItems.size());
        for (PurchaseReceiptItemEntity line : receiptItems) {
            PurchaseOrderItemEntity orderItem = orderItems.get(line.getPurchaseOrderItemId());
            if (orderItem == null) {
                throw new ScmBusinessException(PURCHASE_ORDER_ITEM_NOT_FOUND);
            }
            inboundLines.add(new InboundLine(line, orderItem, line.getReceivedQuantity(),
                lotsByItem.getOrDefault(line.getId(), List.of())));
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        receipt.setPutawayStatus(ScmPutawayStatusEnum.COMPLETED.name());
        receipt.setPutawayAt(now);
        receipt.setPutawayBy(operator);
        stamp(receipt, false);
        if (purchaseReceiptDao.updateById(receipt) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        Map<String, Object> before = PurchaseSnapshotFactory.snapshot();
        before.put("putawayStatus", ScmPutawayStatusEnum.PENDING.name());
        Map<String, Object> after = PurchaseSnapshotFactory.snapshot();
        after.put("putawayStatus", ScmPutawayStatusEnum.COMPLETED.name());
        // 审计记录只放字符串：PurchaseJsonbTypeHandler 的 ObjectMapper 未注册 JavaTimeModule，
        // 直接放 OffsetDateTime 会抛「Invalid purchase JSON」（与确认日志同一规则）。
        after.put("putawayAt", now.toString());
        after.put("putawayBy", operator);
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
            ScmPurchaseOperationTypeEnum.RECEIPT_PUTAWAY, order.getId(), receipt.getId(), null, before, after));

        // 位置固定：putaway 状态保存之后、幂等 complete 之前（与 confirm 同一规则）。
        postInbound(order, receipt, inboundLines, now, operator);

        PurchaseReceiptVO result = purchaseQueryService.receiptDetailForCommand(receipt.getId());
        purchaseIdempotencyService.complete(claim, ScmFinancePayableSourceTypeEnum.PURCHASE_RECEIPT.name(),
            receipt.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(PurchaseReceiptDeleteForm form) {
        PurchaseReceiptEntity receipt = purchaseReceiptDao.lock(form.getId());
        if (receipt == null) {
            // 幂等：已删除视为成功（与单张删除一致）
            return;
        }
        purchaseOwnerResolver.requireVisible(orderPurchaserId(receipt));
        if (!ScmReceiptStatusEnum.DRAFT.name().equals(receipt.getStatus())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_DELETE_STATE_INVALID);
        }

        PurchaseReceiptVO before = purchaseQueryService.receiptDetailForCommand(receipt.getId());
        purchaseReceiptItemDao.softDeleteByReceiptId(receipt.getId(), ScmOperator.current());
        if (purchaseReceiptDao.softDelete(receipt.getId(), receipt.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        Map<String, Object> after = PurchaseSnapshotFactory.snapshot();
        after.put("deleted", true);
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(ScmPurchaseOperationTypeEnum.RECEIPT_DELETE,
            receipt.getPurchaseOrderId(), receipt.getId(), null, receiptSnapshot(before), after));
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(PurchaseReceiptBatchDeleteForm form) {
        // 按 id 升序：批量删除也必须遵守确定性锁序
        form.getReceipts().stream()
            .sorted(Comparator.comparing(PurchaseReceiptBatchDeleteForm.PurchaseReceiptVersionForm::getId))
            .forEach(row -> delete(deleteForm(row)));
    }

    /**
     * 请求行集合必须恰好等于本收货单的全部活动行集合。
     *
     * <p>
     * 不允许只提交子集：否则「确认了但仍有 0 数量行」的歧义会一直存在，{@code received_quantity} 的累计规则也会变得难以推理。
     */
    private static Map<Long, PurchaseReceiptConfirmForm.Item> requestedLines(PurchaseReceiptConfirmForm form,
        List<PurchaseReceiptItemEntity> receiptItems) {
        Map<Long, PurchaseReceiptConfirmForm.Item> requested = new LinkedHashMap<>();
        for (PurchaseReceiptConfirmForm.Item item : form.getItems()) {
            if (requested.put(item.getReceiptItemId(), item) != null) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_ITEM_INCOMPLETE);
            }
        }
        Set<Long> active = receiptItems.stream().map(PurchaseReceiptItemEntity::getId).collect(Collectors.toSet());
        if (active.isEmpty() || !active.equals(requested.keySet())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_ITEM_INCOMPLETE);
        }
        return requested;
    }

    /**
     * 收货确认后才调用库存入库。操作人和发生时间必须取已保存的收货确认记录；先锁收货单，再按仓库、商品规格升序锁库存余额。
     */
    private void postInbound(PurchaseOrderEntity order, PurchaseReceiptEntity receipt, List<InboundLine> lines,
        OffsetDateTime occurredAt, String operator) {
        if (lines.isEmpty()) {
            // confirm 要求请求行 == 活动行且非空（40998），因此这里只是防御性短路
            return;
        }
        List<PurchaseInventoryContract.InboundFact> facts = lines.stream()
            .map(line -> new PurchaseInventoryContract.InboundFact(order.getId(), receipt.getId(), line.line().getId(),
                order.getWarehouseId(), line.line().getSkuId(), receipt.getWarehouseCodeSnapshot(),
                receipt.getWarehouseNameSnapshot(), line.line().getSkuCodeSnapshot(), line.line().getSkuNameSnapshot(),
                // 单位统一取 purchase_unit_snapshot，不能用 confirm 循环里的 weightUnit（标品时为 null）
                line.orderItem().getPurchaseUnitSnapshot(), line.effective(), line.orderItem().getPurchasePrice(),
                PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE + ":" + line.line().getId(), occurredAt, operator,
                line.lots().stream()
                    .map(lot -> new PurchaseInventoryContract.LotQuantity(lot.lotId(), lot.lotNo(), lot.quantity()))
                    .toList()))
            .toList();

        facts.stream().sorted(inboundLockOrder()).forEach(purchaseInventoryContract::postInbound);
    }

    /**
     * 采购入库必须最后获取库存余额锁，并按仓库 ID、商品规格 ID 升序锁定，避免两个收货事务相互等待。
     */
    static Comparator<PurchaseInventoryContract.InboundFact> inboundLockOrder() {
        return Comparator.comparing(PurchaseInventoryContract.InboundFact::warehouseId)
            .thenComparing(PurchaseInventoryContract.InboundFact::skuId);
    }

    /**
     * 一行入库事实的最小元组。
     *
     * <p>
     * 刻意不在收货循环里直接装配 {@code InboundFact}：那时 {@code receipt} 还没保存成 CONFIRMED， {@code confirmedAt} / {@code operator} 仍是
     * null，用它装配出来的流水会把「发生时刻」写成 null。
     */
    private record InboundLine(PurchaseReceiptItemEntity line, PurchaseOrderItemEntity orderItem, BigDecimal effective,
        List<TraceLotContract.LotAllocation> lots) {
    }

    /**
     * 按仓库开关校验并登记本行批次。
     *
     * <p>
     * 批次仓：批次必填、数量之和必须等于本次有效收货量，来源批号可为空；非批次仓：不允许携带批次，行为与以前一致。
     */
    private List<TraceLotContract.LotAllocation> resolveLots(boolean lotTracking, PurchaseOrderEntity order,
        PurchaseReceiptItemEntity line, PurchaseReceiptConfirmForm.Item input, BigDecimal effective, String operator) {
        List<PurchaseReceiptConfirmForm.Lot> drafts = input.getLots();
        if (!lotTracking) {
            if (drafts != null && !drafts.isEmpty()) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_LOT_NOT_TRACKED);
            }
            return List.of();
        }
        if (drafts == null || drafts.isEmpty()) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_LOT_REQUIRED);
        }
        BigDecimal sum = BigDecimal.ZERO;
        List<TraceLotContract.LotDraft> lotDrafts = new ArrayList<>(drafts.size());
        for (PurchaseReceiptConfirmForm.Lot draft : drafts) {
            BigDecimal quantity = PurchaseReceiptQuantityCalculator.declared(draft.getQuantity());
            sum = sum.add(quantity);
            lotDrafts.add(new TraceLotContract.LotDraft(draft.getSourceLotNo(), quantity));
        }
        if (sum.compareTo(effective) != 0) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_LOT_QUANTITY_MISMATCH);
        }
        return traceLotContract.createReceiptLots(new TraceLotContract.CreateReceiptLots(line.getId(), line.getSkuId(),
            order.getSupplierId(), operator, lotDrafts));
    }

    /**
     * 上架时按收货行回读已登记批次；未启用批次的仓库自然得到空集合。
     */
    private Map<Long, List<TraceLotContract.LotAllocation>> lotsByReceiptItem(List<PurchaseReceiptItemEntity> items) {
        List<Long> ids = items.stream().map(PurchaseReceiptItemEntity::getId).toList();
        Map<Long, List<TraceLotContract.LotAllocation>> grouped = new LinkedHashMap<>();
        for (TraceLotVO.ReceiptItemLot lot : traceLotContract.listByReceiptItemIds(ids)) {
            grouped.computeIfAbsent(lot.getReceiptItemId(), key -> new ArrayList<>())
                .add(new TraceLotContract.LotAllocation(lot.getLotId(), lot.getLotNo(), lot.getSourceLotNo(),
                    lot.getQuantity()));
        }
        return grouped;
    }

    private void appendWeighingRecord(Long receiptItemId, BigDecimal actualWeight, String weightUnit,
        String correctionReason) {
        ReceiptWeighingRecordEntity record = new ReceiptWeighingRecordEntity();
        record.setPurchaseReceiptItemId(receiptItemId);
        // 手工录入：原始读数与确认读数同值；{@code DEVICE} 是后续扩展点（CHECK 目前只允许 MANUAL）
        record.setRawReading(actualWeight);
        record.setConfirmedReading(actualWeight);
        record.setUnit(weightUnit);
        record.setSource(ScmWeighingSourceEnum.MANUAL.name());
        record.setModificationReason(correctionReason);
        record.setRecordedAt(OffsetDateTime.now());
        record.setOperator(ScmOperator.current());
        record.setCreatedAt(record.getRecordedAt());
        record.setCreatedBy(record.getOperator());
        receiptWeighingRecordDao.append(record);
    }

    private PurchaseReceiptEntity lockReceipt(Long purchaseReceiptId) {
        PurchaseReceiptEntity receipt = purchaseReceiptDao.lock(purchaseReceiptId);
        if (receipt == null) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_NOT_FOUND);
        }
        return receipt;
    }

    /**
     * 收货单上没有归属列，采购归属只在父单头上，因此写侧守卫要先读父单的 {@code purchaser_id}。
     *
     * <p>
     * 刻意用非锁定读：归属只能经 {@code /scm/purchase/reassign} 移动，而那条路径自身受分配权与改派审计约束；在这里锁父单会把一次普通的备注编辑拖进采购单的锁序里。
     */
    private Long orderPurchaserId(PurchaseReceiptEntity receipt) {
        PurchaseOrderEntity order = purchaseOrderDao.selectById(receipt.getPurchaseOrderId());
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        return order.getPurchaserId();
    }

    private static void version(Integer actual, Integer expected) {
        if (!Objects.equals(actual, expected)) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private static PurchaseReceiptDeleteForm deleteForm(PurchaseReceiptBatchDeleteForm.PurchaseReceiptVersionForm row) {
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

}
