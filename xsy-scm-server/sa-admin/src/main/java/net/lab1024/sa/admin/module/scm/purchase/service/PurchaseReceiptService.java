package net.lab1024.sa.admin.module.scm.purchase.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmWarehouseScopeGuard;
import net.lab1024.sa.admin.module.scm.finance.service.FinancePayableService;
import net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseConfigKey;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPutawayStatusEnum;
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
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderStateMachine;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderValidator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseReceiptPutawayGuard;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseReceiptQuantityCalculator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseOwnerResolver;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseWarehouseReferenceGuard;
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
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_PUTAWAY_STATE_INVALID;
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
 * <p><b>W6 库存接线</b>：{@code confirm} 的最后一步（§4.3 第 15 步）调用
 * {@link PurchaseInventoryContract#postInbound}，**在同一个事务内**。
 * 本类只依赖 W5 已定义的接口，**不 import inventory 模块任何类** ——
 * purchase → inventory 的编译期依赖为零，真实实现由 Spring 在装配期注入。
 *
 * <p><b>Finance R1 应付接线</b>：{@code confirm} 把收货单置为 {@code CONFIRMED} 之后调用
 * {@link FinancePayableService#generateOnReceiptConfirm}，与库存写入同一个事务（第一批 Q9）。
 * 这里不需要 W6 那样的接口：依赖方向是 purchase → finance，而 finance 对采购表只读、
 * 不反向 import 采购域，因此不存在环。
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

    /**
     * 库存契约（W6 接线）。运行时注入的是 {@code inventory.support.PurchaseInventoryContractImpl}；
     * W5 期间容器里没有该类型的 Bean，本字段是 W6 新增的唯一依赖。
     */
    private final PurchaseInventoryContract purchaseInventoryContract;

    /**
     * 仓库引用守卫：收货单创建 / 入库确认前断言仓库仍启用（40987）。
     */
    private final PurchaseWarehouseReferenceGuard warehouseReferenceGuard;

    /**
     * 仓库维度的写侧守卫：{@code DIRECT} 收货确认与 {@code WAREHOUSE_CONFIRM} 的上架都会写
     * {@code PURCHASE_IN}，因此两条路径都要判仓库授权；不写库存的收货单编辑/删除不判。
     */
    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    /**
     * 采购归属维度的写侧守卫：收货单挂在采购单上，父单归属不在调用者范围内即拒绝。
     * 与 {@link #warehouseScopeGuard} 是两条独立边界，DIRECT 确认要求同时成立。
     */
    private final PurchaseOwnerResolver ownerResolver;

    /**
     * 应付生成器（Finance R1 F1-2A）：收货确认在同一事务内派生正常应付。
     * 生成失败即整笔收货确认回滚 —— 财务侧不接「业务已确认但账上什么都没有」这个缺口。
     */
    private final FinancePayableService financePayableService;

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
        ownerResolver.requireVisible(order.getPurchaserId());
        if (!PurchaseOrderStateMachine.receivable(order.getStatus())) {
            // RECEIVED / SHORT_CLOSED / CANCELLED / DRAFT 都不允许新收货（T8）
            throw new ScmBusinessException(PURCHASE_RECEIPT_ORDER_STATE_INVALID);
        }
        // 仓库可能在采购单创建后被停用：收货单创建是「新引用」，必须重查启用态（HD-B1-01）。
        warehouseReferenceGuard.requireEnabled(order.getWarehouseId());
        List<PurchaseOrderItemEntity> orderItems = purchaseOrderItemDao.lockByOrderId(order.getId());
        if (orderItems.isEmpty()) {
            throw new ScmBusinessException(PURCHASE_ORDER_ITEM_EMPTY);
        }

        PurchaseReceiptEntity receipt =
                PurchaseSnapshotFactory.receipt(order, numberGenerator.receipt(), form.getRemark());
        // B1：入库方式由调用方显式二选一（无默认）；DRAFT 期入库状态恒 PENDING。
        receipt.setReceiptMode(form.getReceiptMode());
        receipt.setPutawayStatus(ScmPutawayStatusEnum.PENDING.name());
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

        PurchaseReceiptVO result = queryService.receiptDetailForCommand(receipt.getId());
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
        ownerResolver.requireVisible(orderPurchaserId(receipt));
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
        return queryService.receiptDetailForCommand(receipt.getId());
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
        // B1：入库方式决定 confirm 是否同事务入库（HD-B1-01/03）。
        boolean direct = ScmReceiptModeEnum.DIRECT.name().equals(receipt.getReceiptMode());
        // 两条边界取交集（裁决「P0 基线收口裁决」第 16 条）：采购范围回答「这张采购单归不归他操作」，
        // 仓库范围回答「货允许不允许落进这个仓」，前者不能替代后者 —— 否则握着采购按钮的人可以往
        // 自己无权管理的仓库里写 PURCHASE_IN。WAREHOUSE_CONFIRM 在 confirm 时不写库存，
        // 因此仓库维度由后续的 putaway 判，不在这里提前收权。
        ownerResolver.requireVisible(order.getPurchaserId());
        if (direct) {
            warehouseScopeGuard.require(receipt.getWarehouseId());
        }
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
        // W6：本行入库事实的最小元组（行 / 采购行 / 有效数量）。**循环内只收集，不调用契约** ——
        // 事实装配必须发生在收货单 CONFIRMED 落库之后（occurredAt/operator 取自那一刻的冻结事实）。
        List<InboundLine> inboundLines = new ArrayList<>(receiptItems.size());

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

            // W6：收集入库事实元组（不在此处调用契约，见 inboundLines 声明处的说明）
            inboundLines.add(new InboundLine(line, orderItem, effective));
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
        if (direct) {
            // DIRECT：确认即物理入库，putaway 与 confirm 同事务完成。
            receipt.setPutawayStatus(ScmPutawayStatusEnum.COMPLETED.name());
            receipt.setPutawayAt(now);
            receipt.setPutawayBy(receipt.getOperator());
        }
        // WAREHOUSE_CONFIRM：此处**不写库存**，putaway_status 保持 PENDING，等待仓库二次确认。
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
        // §4.3 第 15 步（W6）：库存入库 —— 与采购侧写入同事务。
        // 位置固定：操作日志之后、幂等 complete 之前。幂等 complete 落在库存写入之后，
        // 保证「重放返回的结果」= 库存已写入的成功结果。
        if (direct) {
            postInbound(order, receipt, inboundLines, receipt.getConfirmedAt(), receipt.getOperator());
        }

        // Finance R1（第一批 Q9）：企业确认收到货即形成供应商债务，仓库何时 putaway 不决定应付时点，
        // 因此 DIRECT 与 WAREHOUSE_CONFIRM 两条路径都只在这里生成一次，putaway 不再调用。
        // 位置在库存写入之后：财务只消费已经成立的收货事实，自身不锁业务表也不锁余额（全局不变量 4），
        // 并发的重复触发由 finance_payable 的来源唯一索引仲裁。抛错即整笔 confirm 回滚。
        financePayableService.generateOnReceiptConfirm(receipt.getId());

        PurchaseReceiptVO result = queryService.receiptDetailForCommand(receipt.getId());
        idempotencyService.complete(claim, "PURCHASE_RECEIPT", receipt.getId(), result);
        return result;
    }

    // ------------------------------------------------------------------
    // receipt.putaway（B1：仓库二次确认入库，独立事务，HD-B1-03）
    // ------------------------------------------------------------------

    /**
     * 仓库确认入库：仅适用于 {@code WAREHOUSE_CONFIRM} 且 {@code putaway_status=PENDING} 的已确认收货单。
     *
     * <p><b>锁序（§9）</b>：收货单（行锁）→ 余额（按 (warehouseId, skuId) 升序）。收货单锁始终先于
     * 余额锁，且 confirm 路径同样是「收货单锁在余额锁之前」，因此不存在 {@code balance → receipt}
     * 的反向路径，与 P12 全局锁序兼容，不会成环。
     *
     * <p><b>并发两次 putaway</b>：只有先拿到收货单锁的那次能通过 {@code putaway_status=PENDING} 校验并
     * 写入库存；后到者读到 {@code COMPLETED} 抛 41008（或由幂等 claim 重放）。数据库侧
     * {@code uk_inventory_movement_source_active} 兜底，绝不重复 PURCHASE_IN。
     *
     * <p><b>occurred_at / operator</b>：流水取本次 putaway 的物理入库时刻与操作人（HD-B1-03），
     * **不得**写成 receipt.confirmed_at。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptVO putaway(PurchaseReceiptPutawayForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(
                "PURCHASE_RECEIPT_PUTAWAY:" + form.getId(), idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseReceiptVO.class);
        }

        PurchaseReceiptEntity receipt = lockReceipt(form.getId());
        // 上架是唯一写 PURCHASE_IN 的采购动作，因此只有它要判仓库授权：判据取收货行上的仓库，
        // 不取请求参数（表单也没有仓库字段）。幂等认领已插入，但随本事务一起回滚，不留痕迹。
        warehouseScopeGuard.require(receipt.getWarehouseId());
        version(receipt.getVersion(), form.getVersion());
        if (!PurchaseReceiptPutawayGuard.putawayAllowed(
                receipt.getStatus(), receipt.getReceiptMode(), receipt.getPutawayStatus())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_PUTAWAY_STATE_INVALID);
        }

        PurchaseOrderEntity order = purchaseOrderDao.selectById(receipt.getPurchaseOrderId());
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        // 已确认收货单的采购单不可能仍是 DRAFT，行价格只读即可（不入行锁）。
        Map<Long, PurchaseOrderItemEntity> orderItems = purchaseOrderItemDao.listByOrderId(order.getId())
                .stream()
                .collect(Collectors.toMap(PurchaseOrderItemEntity::getId, Function.identity(), (a, b) -> a));
        List<PurchaseReceiptItemEntity> receiptItems = purchaseReceiptItemDao.listByReceiptId(receipt.getId());

        // 入库事实的最小元组：数量取确认时已落库的 received_quantity，单位/单价取采购行快照。
        List<InboundLine> inboundLines = new ArrayList<>(receiptItems.size());
        for (PurchaseReceiptItemEntity line : receiptItems) {
            PurchaseOrderItemEntity orderItem = orderItems.get(line.getPurchaseOrderItemId());
            if (orderItem == null) {
                throw new ScmBusinessException(PURCHASE_ORDER_ITEM_NOT_FOUND);
            }
            inboundLines.add(new InboundLine(line, orderItem, line.getReceivedQuantity()));
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
        // 审计快照只放字符串：PurchaseJsonbTypeHandler 的 ObjectMapper 未注册 JavaTimeModule，
        // 直接放 OffsetDateTime 会抛「Invalid purchase JSON」（与确认日志同纪律）。
        after.put("putawayAt", now.toString());
        after.put("putawayBy", operator);
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.RECEIPT_PUTAWAY, order.getId(), receipt.getId(),
                null, before, after));

        // 位置固定：putaway 状态落库之后、幂等 complete 之前（与 confirm 同纪律）。
        postInbound(order, receipt, inboundLines, now, operator);

        PurchaseReceiptVO result = queryService.receiptDetailForCommand(receipt.getId());
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
        ownerResolver.requireVisible(orderPurchaserId(receipt));
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_DELETE_STATE_INVALID);
        }

        PurchaseReceiptVO before = queryService.receiptDetailForCommand(receipt.getId());
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

    /**
     * §4.3 第 15 步（W6）：把本次确认的收货行交给库存域入库。
     *
     * <p><b>事实装配的取值纪律（Q13-附）</b>：{@code occurredAt} / {@code operator} 一律取
     * **已落库的收货确认事实**（{@code receipt.getConfirmedAt()} / {@code receipt.getOperator()}），
     * 而不是在库存侧现取 {@code now()} 或 ambient operator。因此本方法必须在
     * 「收货单 → CONFIRMED 落库」之后调用（调用点见 {@code confirm}）。
     *
     * <p><b>锁序（§8.1 / §8.4）</b>：余额锁是事务里最后获取的锁，且多把余额锁之间必须按
     * {@code (warehouseId, skuId)} **升序**获取 —— 任意两个并发 confirm 的加锁顺序因此一致，
     * 这是防死锁的关键，也是「排序发生在调用方而不是实现侧」的原因
     * （实现侧内部缓冲会把事务状态留在契约实现里）。
     *
     * <p><b>失败传播</b>：库存写入抛出的任何异常都会冒泡出去，使整个 confirm 回滚 ——
     * {@code received_quantity} 累计、对账快照、称重记录、采购单状态、收货单状态、操作日志、
     * 幂等记录全部不落库。用户视角 = 「这次收货确认失败了」，重试安全（幂等 claim 未提交）。
     */
    private void postInbound(PurchaseOrderEntity order, PurchaseReceiptEntity receipt,
                             List<InboundLine> lines, OffsetDateTime occurredAt, String operator) {
        if (lines.isEmpty()) {
            // confirm 要求请求行 == 活动行且非空（40998），因此这里只是防御性短路
            return;
        }
        List<PurchaseInventoryContract.InboundFact> facts = lines.stream()
                .map(line -> new PurchaseInventoryContract.InboundFact(
                        order.getId(),
                        receipt.getId(),
                        line.line().getId(),
                        order.getWarehouseId(),
                        line.line().getSkuId(),
                        receipt.getWarehouseCodeSnapshot(),
                        receipt.getWarehouseNameSnapshot(),
                        line.line().getSkuCodeSnapshot(),
                        line.line().getSkuNameSnapshot(),
                        // 审计提醒（Legacy Audit §7.3）：单位统一取 purchase_unit_snapshot，
                        // **不得**用 confirm 循环里的 weightUnit（标品时为 null）
                        line.orderItem().getPurchaseUnitSnapshot(),
                        line.effective(),
                        line.orderItem().getPurchasePrice(),
                        PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE + ":" + line.line().getId(),
                        occurredAt,
                        operator))
                .toList();

        facts.stream()
                .sorted(inboundLockOrder())
                .forEach(purchaseInventoryContract::postInbound);
    }

    /**
     * 余额加锁顺序（W6 §8.1 / §8.4）：按 {@code (warehouseId, skuId)} 字典序升序。
     *
     * <p><b>为什么必须排好序再逐条调用</b>：库存余额锁是整个事务里**最后**获取的锁。
     * 如果两个并发事务各自按「采购单行的自然顺序」去锁余额，就可能出现
     * A 先锁 {@code (w1,k1)} 再锁 {@code (w2,k2)}、B 先锁 {@code (w2,k2)} 再锁 {@code (w1,k1)} ——
     * 加锁方向相反即形成等待环，PostgreSQL 只能靠死锁检测牺牲其中一个事务
     * （表现为「偶发的收货失败」，最难排查的一类缺陷）。统一升序后任意两个事务的
     * 加锁方向一致，环不可能形成。
     *
     * <p><b>为什么抽成具名方法而不是内联 lambda</b>：这条纪律的正确性由单测
     * {@code PurchaseInboundLockOrderTest} 锁住，而内联 lambda 无法被直接断言 ——
     * 「不可测的纪律」等于「没有纪律」。
     */
    static Comparator<PurchaseInventoryContract.InboundFact> inboundLockOrder() {
        return Comparator.comparing(PurchaseInventoryContract.InboundFact::warehouseId)
                .thenComparing(PurchaseInventoryContract.InboundFact::skuId);
    }

    /**
     * 一行入库事实的最小元组（W6 §6.3）。
     *
     * <p>刻意**不**在收货循环里直接装配 {@code InboundFact}：那时 {@code receipt} 还没落库成
     * CONFIRMED，{@code confirmedAt} / {@code operator} 仍是 null —— 用它装配出来的流水
     * 会把「发生时刻」写成 null，或者被迫在库存侧补一个 {@code now()}。
     */
    private record InboundLine(PurchaseReceiptItemEntity line,
                               PurchaseOrderItemEntity orderItem,
                               BigDecimal effective) {
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

    /**
     * 收货单上没有归属列，采购归属只在父单头上，因此写侧守卫要先读父单的 {@code purchaser_id}。
     *
     * <p>刻意用非锁定读：归属只能经 {@code /scm/purchase/reassign} 移动，而那条路径自身受分配权
     * 与改派审计约束；在这里锁父单会把一次普通的备注编辑拖进采购单的锁序里。
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

    /**
     * `RECEIPT_DELETE` 的「全量」前态（§7.12）。
     */
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
