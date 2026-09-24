package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryLossGainTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryMovementDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryConversionFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryLossGainFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryOutboundFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryStocktakeAdjustment;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryStocktakeFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryTransferFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryMovementEntity;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_INSUFFICIENT_AVAILABLE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_SOURCE_BALANCE_MISSING;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_SOURCE_UNIT_MISMATCH;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_TARGET_UNIT_MISMATCH;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_CONVERSION;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_INBOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_LOSS_GAIN;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_OUTBOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_STOCKTAKE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_TRANSFER;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_INSUFFICIENT_AVAILABLE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_BALANCE_MISSING;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_BELOW_RESERVED;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_NEGATIVE_AFTER;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_BALANCE_MISSING;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_BELOW_RESERVED;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_NEGATIVE_AFTER;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_INSUFFICIENT_AVAILABLE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_SOURCE_BALANCE_MISSING;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_UNIT_MISMATCH;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_UNIT_MISMATCH;

/**
 * 将已确认的采购收货行记入库存，必须参与调用方事务，失败时随收货确认一起回滚。
 *
 * <p>调用方须先持有采购/收货锁，再按 {@code (warehouseId, skuId)} 升序写入库存，
 * 避免多行收货以相反顺序获取余额锁。
 *
 * <p><b>本类是全仓唯一会改变 {@code inventory_balance.quantity} 的地方</b>，
 * 八条写入路径都遵守同一套纪律：
 * <ul>
 *   <li>{@link #postPurchaseInbound} —— 采购入库（方向 = 入，可建零余额行）；</li>
 *   <li>{@link #postSalesOutbound} —— 销售出库（方向 = 出，不建行，先判可用量）；</li>
 *   <li>{@link #postStocktakeAdjust} —— 盘点调整（方向由差异正负决定，不建行）；</li>
 *   <li>{@link #postLossGainAdjust} —— 报损报溢（方向由单据类型决定，不建行）；</li>
 *   <li>{@link #postTransferOut} —— 调拨转出（方向 = 出，不建行，先判可用量）；</li>
 *   <li>{@link #postTransferIn} —— 调拨转入（方向 = 入，**可建零余额行**）；</li>
 *   <li>{@link #postConvertOut} —— 规格转换转出（方向 = 出，不建行，先判可用量）；</li>
 *   <li>{@link #postConvertIn} —— 规格转换转入（方向 = 入，**可建零余额行**）。</li>
 * </ul>
 * 八者都：不自行开启事务、先锁余额行再算 before/after、只用**增量**方法改余额
 * （{@code incrementQuantity} / {@code decrementQuantity}），从不做「读-改-写」赋值。
 *
 * <p><b>均价（V34）只由三条入方向腿按加权公式更新</b>：采购入库、调拨转入、规格转换转入
 * （{@link #inboundAvgCost}）。三者的成本来源分别是采购单价、{@link #transferInCost} 回读的
 * 发出腿事实，以及调用方在写腿之前解好的成本基准（{@link #lockCostBasis} 的期初快照 +
 * 链式求解）。出方向与其它入库都不改均价，只把当时的均价写进流水。
 *
 * <p><b>「入方向才允许建零余额行」是一条不变量</b>：三条入方向腿可以建行（它们带单位事实），
 * 五条出方向腿（销售出库 / 盘点 / 报损报溢 / 调拨转出 / 转换转出）都不建
 * （无余额行意味着「从未入库」，对「出」方向就是无货可动）。
 */
@Service
@RequiredArgsConstructor
public class InventoryCommandService {

    private final InventoryBalanceDao balanceDao;

    private final InventoryMovementDao movementDao;

    private final WarehouseService warehouseService;

    /**
     * 追加采购入库流水并累加余额；数量必须为正且单位须与已有余额一致。
     * 来源行重复时抛错，不将异常重复记账当作成功重放。
     */
    public void postPurchaseInbound(PurchaseInventoryContract.InboundFact fact) {
        // 不自行开启事务，防止脱离收货确认后分步提交。
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Purchase inbound requires the caller's transaction");
        }
        requireFact(fact);

        warehouseService.require(fact.warehouseId());

        // 并发首建冲突后仍锁定同一余额行；SQL 冲突目标须匹配部分唯一索引。
        balanceDao.insertOnConflictDoNothing(
                fact.warehouseId(), fact.skuId(), fact.unit(), fact.operator());
        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }

        // 一个仓库 + SKU 只允许一个记账单位，不进行隐式换算。
        if (!fact.unit().equals(balance.getUnit())) {
            throw new ScmBusinessException(INVENTORY_UNIT_MISMATCH);
        }

        // 持有余额行锁后读取期初数量，避免并发入库生成错误快照。
        BigDecimal before = balance.getQuantity();
        BigDecimal after = before.add(fact.quantity());

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.PURCHASE_IN.name());
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.PURCHASE_RECEIPT_ITEM.name());
        movement.setSourceDocumentId(fact.receiptId());
        movement.setSourceDocumentItemId(fact.receiptItemId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(fact.unit());
        movement.setUnitCost(fact.unitCost());
        movement.setBeforeQuantity(before);
        movement.setAfterQuantity(after);
        // 使用收货确认时刻和操作者，不能改用当前时间或当前登录人。
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (movementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_INBOUND);
        }

        // V34：均价（加权）由三条入方向腿更新 —— 采购入库、调拨转入、规格转换转入，口径见类 Javadoc；
        // 本方法是其中的采购入库腿，按 (旧量·旧均价 + 入量·入价) / 新量 重算。
        // 数量增量与均价写入合并成**一条**语句 —— 一次逻辑行变更只自增一次 version。
        // 报溢等不带成本依据的入库走普通的 incrementQuantity，均价不变；
        // 出库更不改均价，只把当时的均价写进流水。
        if (balanceDao.incrementQuantityAndSetAvgCost(balance.getId(), fact.quantity(),
                inboundAvgCost(balance, fact.quantity(), fact.unitCost()), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 采购入库后的移动加权均价（V34）。**纯计算，不落库** ——
     * 结果由 {@link InventoryBalanceDao#incrementQuantityAndSetAvgCost} 与数量增量一起写入，
     * 让一次逻辑行变更只自增一次 {@code version}。
     *
     * <p>入参 {@code balance} 必须是**持有行锁之后**读到的快照，否则加权基数会是过期的。
     * 公式与取整口径见 {@link #weightedAvgCost}。
     */
    private static BigDecimal inboundAvgCost(InventoryBalanceEntity balance, BigDecimal inboundQuantity,
                                             BigDecimal inboundUnitCost) {
        return weightedAvgCost(balance.getQuantity(), balance.getAvgCost(), inboundQuantity, inboundUnitCost);
    }

    /**
     * 移动加权均价 —— 全项目**唯一**一份加权公式实现，转换腿的成本基准求解也走它。
     *
     * <pre>
     * newAvg = (旧量·旧均价 + 入量·入价) / (旧量 + 入量)     四舍五入到 4 位小数
     * </pre>
     *
     * <p>取整方式固定为 {@code HALF_UP}：四舍五入是财务口径的默认，而 {@code HALF_EVEN}
     * （银行家舍入）会让「同一批数据算两遍」的结果依赖于末位奇偶，对账时难以解释。
     *
     * <p>均价为空按 0 计入 —— 那意味着「这批货没有成本事实」。把它当作 0 会把均价拉低，
     * 这是**刻意的**：宁可在均价上体现「有一批货成本未知」，也不要凭空编一个价格。
     * 采购收货的 unit_cost 一定非空；实际会走到空值分支的只有成本核算（V34）上线前已发出、
     * 上线后才收货的在途调拨。
     */
    public static BigDecimal weightedAvgCost(BigDecimal beforeQuantity, BigDecimal beforeAvgCost,
                                             BigDecimal inboundQuantity, BigDecimal inboundUnitCost) {
        BigDecimal oldQuantity = beforeQuantity == null ? BigDecimal.ZERO : beforeQuantity;
        BigDecimal oldAvg = beforeAvgCost == null ? BigDecimal.ZERO : beforeAvgCost;
        BigDecimal inCost = inboundUnitCost == null ? BigDecimal.ZERO : inboundUnitCost;
        BigDecimal newQuantity = oldQuantity.add(inboundQuantity);
        if (newQuantity.signum() == 0) {
            // 入量恒为正（DB CHECK），所以这里不可达；留作防御，避免除零。
            return oldAvg;
        }
        return oldQuantity.multiply(oldAvg)
                .add(inboundQuantity.multiply(inCost))
                .divide(newQuantity, 4, RoundingMode.HALF_UP);
    }

    /**
     * 在任何库存腿写入之前，锁定本次动作涉及的余额行并快照**期初数量与均价**。
     *
     * <p>规格转换需要它有两个原因：① 锁序 —— 调用方把明细拆成腿后按 {@code (skuId, 先入后出)}
     * 排序执行，这里按 {@code skuId} 升序把全部行一次锁齐，腿后续的 {@code FOR UPDATE}
     * 只是重入本事务已持有的行锁；② 成本基准的**期初输入** —— 同一条明细的转入腿可能先于
     * 转出腿执行，到了腿里再读均价会读到被本单前半段改过的值，两条腿于是不同源、总成本不守恒。
     * 链式转换（某 SKU 在本单内既进又出）的基准由调用方在这份期初快照之上求解
     * （见 {@code InventoryConversionService#resolveOutboundCostBasis}）：腿的执行顺序保证
     * 同一 SKU 先入后出，所以它的转出腿在真实账本上看到的正是「进完之后」的均价。
     *
     * @param skuIds 本单涉及的源与目标 SKU（重复与无序都可以，内部按升序去重锁定）
     * @return {@code skuId -> 期初数量与均价}；没有余额行的 SKU 不在结果里，调用方按 0 处理
     */
    public Map<Long, CostBasis> lockCostBasis(Long warehouseId, Collection<Long> skuIds) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Cost basis snapshot requires the caller's transaction");
        }
        Map<Long, CostBasis> basis = new HashMap<>();
        skuIds.stream().filter(Objects::nonNull).distinct().sorted().forEach(skuId -> {
            InventoryBalanceEntity balance = balanceDao.lockByWarehouseAndSku(warehouseId, skuId);
            // 没有余额行 = 从未入库 = 没有成本事实：出库腿会因此在后面失败（41058），
            // 建行的入腿则以 0 起步，随后被本次转入的均价覆盖。
            if (balance != null) {
                basis.put(skuId, new CostBasis(balance.getQuantity(), balance.getAvgCost()));
            }
        });
        return basis;
    }

    /**
     * 一行余额的成本期初：数量与均价**必须同源**（加权公式的两个自变量）。
     *
     * @param quantity 持锁后读到的账面数量
     * @param avgCost  持锁后读到的移动加权均价，可为空（按 0 计）
     */
    public record CostBasis(BigDecimal quantity, BigDecimal avgCost) {

        public BigDecimal quantityOrZero() {
            return quantity == null ? BigDecimal.ZERO : quantity;
        }

        public BigDecimal avgCostOrZero() {
            return avgCost == null ? BigDecimal.ZERO : avgCost;
        }
    }

    /**
     * 转换后每个**目标单位**的成本 = 源腿总成本 ÷ 目标腿数量（{@code HALF_UP} 到 4 位，
     * 与 {@code avg_cost} 的精度一致）。
     *
     * <p>折算率是人工声明的（一箱是 9.5 还是 10 kg 取决于供应商与批次），所以跨单位的
     * 单价必然不同 —— 守恒的是总成本，不是单价。
     */
    public static BigDecimal convertedUnitCost(BigDecimal sourceQuantity, BigDecimal sourceUnitCost,
                                               BigDecimal targetQuantity) {
        if (targetQuantity.signum() <= 0) {
            throw new IllegalArgumentException("Conversion target quantity must be positive: " + targetQuantity);
        }
        return sourceQuantity.multiply(sourceUnitCost)
                .divide(targetQuantity, 4, RoundingMode.HALF_UP);
    }

    /**
     * 追加销售出库流水并扣减余额，必须参与调用方事务。
     *
     * <p>与入库的三点差异：
     * <ol>
     *   <li><b>不建零余额行</b>：出库时若该 (仓库, SKU) 没有余额行，说明从来没有入过库，
     *       直接判可用量不足（41004），而不是先建一行 0 再扣成负数；</li>
     *   <li><b>可用量门槛</b>：{@code 可用量 = quantity - reserved_quantity}，
     *       出库不得吃掉已预留的货（否则 DB 的 {@code ck_inventory_balance_available} 会拒绝）；</li>
     *   <li><b>方向</b>：{@code after = before - quantity}，与
     *       {@code ck_inventory_movement_snap} 的出库分支一致。</li>
     * </ol>
     *
     * <p><b>单位由余额决定，不由调用方传入</b>：Q13 规定 {@code (warehouse_id, sku_id)} 的记账单位
     * 以余额为准。让调用方传一个单位再与余额比对，等于要求调用方先查一次余额，
     * 既多一次查询、又把「谁是权威」搞反。这里直接取余额单位写入流水快照，
     * 并把该单位返回给调用方回写单据行。
     *
     * @return 本次出库使用的记账单位（调用方用于回写 {@code unitSnapshot}）
     */
    public String postSalesOutbound(InventoryOutboundFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Sales outbound requires the caller's transaction");
        }
        requireOutboundFact(fact);

        warehouseService.require(fact.warehouseId());

        // 出库不建行：没有余额行 = 从未入库 = 无货可出。
        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_INSUFFICIENT_AVAILABLE);
        }

        String unit = balance.getUnit();

        // 持有行锁后再算可用量，避免并发下读到过期快照。
        BigDecimal onHand = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null
                ? BigDecimal.ZERO : balance.getReservedQuantity();
        BigDecimal available = onHand.subtract(reserved);
        if (available.compareTo(fact.quantity()) < 0) {
            throw new ScmBusinessException(INVENTORY_INSUFFICIENT_AVAILABLE);
        }

        BigDecimal after = onHand.subtract(fact.quantity());

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.SALES_OUT.name());
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.SALES_OUTBOUND_ITEM.name());
        movement.setSourceDocumentId(fact.outboundId());
        movement.setSourceDocumentItemId(fact.outboundItemId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(unit);
        // V34：出库**按当时的移动加权均价**记成本（此前留空）。
        // 出库不改变均价，所以余额不动，只把成本写进流水 —— 这是成本核算的核心语义变更。
        movement.setUnitCost(balance.getAvgCost());
        movement.setBeforeQuantity(onHand);
        movement.setAfterQuantity(after);
        // 与入库同纪律：用出库确认时刻与确认人，不能改用当前时间或当前登录人。
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (movementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_OUTBOUND);
        }

        if (balanceDao.decrementQuantity(balance.getId(), fact.quantity(), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return unit;
    }

    /**
     * 按盘点结果调整余额并追加盘盈 / 盘亏流水，必须参与调用方事务。
     *
     * <p><b>差异施加到「当前账面量」而不是「快照」</b>（本波次的核心口径）：
     * <pre>
     * delta = actualQuantity - bookQuantity   // 清点发现的差异，基线是保存草稿时的快照
     * after = live + delta                    // live 是**此刻持锁读到的**账面量
     * </pre>
     * 之所以不直接把账面量改写成实盘数：保存草稿到确认之间很可能发生了收货 / 出库
     * （鲜活品仓里这是常态），直接改写会把那些真实变动悄悄抹掉，而抹掉的痕迹在任何
     * 报表里都看不出来。用「施加差异」的写法，期间发生的变动被完整保留，
     * 且当期间无变动时 {@code after} 恰好等于实盘数 —— 符合直觉。
     *
     * <p><b>delta 为 0 时不写流水</b>：{@code quantity} 恒为正（{@code ck_inventory_movement_qty}），
     * 写不出「零差异」的流水；强行写一条 0 会让「一行一流水」的防重索引语义变脏。
     * 调用方据 {@code movementWritten} 区分「调整了 0」与「无需调整」。
     *
     * <p><b>不建零余额行</b>：记账单位（Q13）只能来自余额行，因此从未入库过的 SKU
     * 不能在盘点里凭空盘盈 —— 那需要先有入库事实来确定单位（41023）。
     *
     * @return 本次调整的结果（记账单位 + 差异 + 调整前后量 + 是否写了流水）
     */
    public InventoryStocktakeAdjustment postStocktakeAdjust(InventoryStocktakeFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Stocktake adjustment requires the caller's transaction");
        }
        requireStocktakeFact(fact);

        warehouseService.require(fact.warehouseId());

        // 盘点不建行：没有余额行 = 从未入库 = 无账可盘，也无法确定记账单位。
        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_BALANCE_MISSING);
        }

        String unit = balance.getUnit();

        // 持有行锁后再读账面量与预留量，避免并发下拿到过期快照。
        BigDecimal live = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null
                ? BigDecimal.ZERO : balance.getReservedQuantity();

        BigDecimal delta = fact.actualQuantity().subtract(fact.bookQuantity());
        BigDecimal after = live.add(delta);

        // Q10：盘亏不得把库存推成负数。DB 的 ck_inventory_balance_quantity 也会拦，
        // 但这里先给出可归因的错误码。
        if (after.signum() < 0) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_NEGATIVE_AFTER);
        }
        // 可用量不为负：已预留的货代表对下游的承诺，不能被盘点吃掉。
        if (after.compareTo(reserved) < 0) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_BELOW_RESERVED);
        }

        if (delta.signum() == 0) {
            return new InventoryStocktakeAdjustment(unit, delta, live, after, false);
        }

        boolean gain = delta.signum() > 0;
        BigDecimal quantity = delta.abs();

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(gain
                ? ScmInventoryMovementTypeEnum.STOCKTAKE_GAIN.name()
                : ScmInventoryMovementTypeEnum.STOCKTAKE_LOSS.name());
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.STOCKTAKE_ITEM.name());
        movement.setSourceDocumentId(fact.stocktakeId());
        movement.setSourceDocumentItemId(fact.stocktakeItemId());
        movement.setQuantity(quantity);
        movement.setUnitSnapshot(unit);
        // unit_cost 记的是本笔移动**当时**的余额均价，盘盈与盘亏两条腿都写，不留空。
        // （「盘点没有成本依据、unit_cost 留空」是移动加权平均上线前的旧语义；V19 的
        //  ck_inventory_movement_cost 允许 NULL 只是容忍历史行，不代表新腿该写空。）
        movement.setUnitCost(balance.getAvgCost());
        movement.setBeforeQuantity(live);
        movement.setAfterQuantity(after);
        // 与入库/出库同纪律：用盘点确认时刻与确认人，不能改用当前时间或当前登录人。
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (movementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_STOCKTAKE);
        }

        int rows = gain
                ? balanceDao.incrementQuantity(balance.getId(), quantity, fact.operator())
                : balanceDao.decrementQuantity(balance.getId(), quantity, fact.operator());
        if (rows != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        return new InventoryStocktakeAdjustment(unit, delta, live, after, true);
    }

    /**
     * 按报损 / 报溢审批结果调整余额并追加流水，必须参与调用方事务。
     *
     * <p><b>方向由单据类型决定，不由数量正负决定</b>：{@code LOSS} → {@code LOSS_REPORT} 且
     * {@code after = before - quantity}；{@code OVERFLOW} → {@code GAIN_REPORT} 且
     * {@code after = before + quantity}。数量恒为正（{@code ck_inventory_movement_qty}），
     * 方向只存在于 {@code movement_type} 里 —— 与 {@code ck_inventory_movement_snap} 的分组一致。
     *
     * <p><b>报损有两条下限，报溢没有上限</b>：
     * <ul>
     *   <li>Q10：{@code after >= 0}，报损不得把库存推成负数（41033）；</li>
     *   <li>可用量：{@code after >= reserved}，报损不得吃掉已预留的货（41034）——
     *       预留代表对下游（销售订单）的承诺，报损不能单方面让它落空。</li>
     * </ul>
     * 报溢只增不减，因此不需要下限判断；但它**不建零余额行**：记账单位（Q13）只能来自
     * 余额行，从未入库过的 SKU 报 41032（与盘点同一约束）。
     *
     * <p><b>用审核时刻与审核人</b>，不是创建时刻与创建人：库存是在审核那一刻变的，
     * 流水的 {@code occurred_at} 必须指向那个时刻，否则「按业务时间查流水」会失真。
     *
     * @return 本次使用的记账单位（调用方用于回写明细行的 {@code unitSnapshot}）
     */
    public String postLossGainAdjust(InventoryLossGainFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Loss/gain adjustment requires the caller's transaction");
        }
        requireLossGainFact(fact);

        // 未知类型直接失败：白名单有 DB CHECK 兜底，但这里先给出可归因的错误码。
        ScmInventoryLossGainTypeEnum type = ScmInventoryLossGainTypeEnum.of(fact.adjustType());
        if (type == null) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_PARAM_INVALID);
        }

        warehouseService.require(fact.warehouseId());

        // 不建零余额行：没有余额行 = 从未入库 = 既无账可调，也无法确定记账单位。
        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_BALANCE_MISSING);
        }

        String unit = balance.getUnit();

        // 持有行锁后再读账面量与预留量，避免并发下拿到过期快照。
        BigDecimal live = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null
                ? BigDecimal.ZERO : balance.getReservedQuantity();

        BigDecimal after;
        if (type.isInbound()) {
            after = live.add(fact.quantity());
        } else {
            after = live.subtract(fact.quantity());
            // Q10：报损不得把库存推成负数。
            if (after.signum() < 0) {
                throw new ScmBusinessException(INVENTORY_LOSS_GAIN_NEGATIVE_AFTER);
            }
            // 可用量不为负：已预留的货不能被报损吃掉。
            if (after.compareTo(reserved) < 0) {
                throw new ScmBusinessException(INVENTORY_LOSS_GAIN_BELOW_RESERVED);
            }
        }

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(type.getMovementType().name());
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.LOSS_GAIN_ITEM.name());
        movement.setSourceDocumentId(fact.lossGainId());
        movement.setSourceDocumentItemId(fact.lossGainItemId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(unit);
        // unit_cost 记的是本笔移动**当时**的余额均价，报损与报溢两条腿都写，不留空。
        // 「报损报溢没有成本依据、留 NULL」是移动加权平均（V34）上线前的旧语义；
        // 出方向因「出库不改变均价」，事后再读余额拿到的仍是同一个值。
        movement.setUnitCost(balance.getAvgCost());
        movement.setBeforeQuantity(live);
        movement.setAfterQuantity(after);
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (movementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_LOSS_GAIN);
        }

        int rows = type.isInbound()
                ? balanceDao.incrementQuantity(balance.getId(), fact.quantity(), fact.operator())
                : balanceDao.decrementQuantity(balance.getId(), fact.quantity(), fact.operator());
        if (rows != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return unit;
    }

    /**
     * 调拨**转出**：从源仓扣减并写 {@code TRANSFER_OUT} 流水（方向 = 出）。
     *
     * <p>与销售出库（{@link #postSalesOutbound}）同一取向：**不建零余额行**
     * （没有余额行 = 从未入库 = 无货可调），先判可用量，再持锁扣减。
     * 可用量 = {@code quantity − reserved_quantity}：调拨不得吃掉源仓已预留的货。
     *
     * <p><b>单位由源仓余额决定，不由调用方传入</b>（Q13）：直接取余额单位写入流水快照，
     * 并返回给调用方回写到明细行 —— 那正是收货时用来断言目标仓单位一致的那个值。
     *
     * @return 本次转出使用的记账单位（= 源仓记账单位）
     */
    public String postTransferOut(InventoryTransferFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Transfer out requires the caller's transaction");
        }
        requireTransferFact(fact);

        warehouseService.require(fact.warehouseId());

        // 转出不建行：没有余额行 = 从未入库 = 无货可调。
        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_SOURCE_BALANCE_MISSING);
        }

        String unit = balance.getUnit();

        // 持有行锁后再算可用量，避免并发下读到过期快照。
        BigDecimal onHand = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null
                ? BigDecimal.ZERO : balance.getReservedQuantity();
        if (onHand.subtract(reserved).compareTo(fact.quantity()) < 0) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_INSUFFICIENT_AVAILABLE);
        }

        BigDecimal after = onHand.subtract(fact.quantity());

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.TRANSFER_OUT.name());
        // 方向编码在**来源类型**里：转出与转入引用同一条明细行，必须落在两个来源类型下，
        // 否则第二条流水会撞上 uk_inventory_movement_source_active。
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.TRANSFER_OUT_ITEM.name());
        movement.setSourceDocumentId(fact.transferId());
        movement.setSourceDocumentItemId(fact.transferItemId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(unit);
        // 转出腿写源仓当时的均价；这条流水同时是收货时回读的转入成本基准（见 transferInCost）。
        movement.setUnitCost(balance.getAvgCost());
        movement.setBeforeQuantity(onHand);
        movement.setAfterQuantity(after);
        // 用**发出**时刻与发出人（不是当前时间 / 当前登录人）。
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (movementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_TRANSFER);
        }

        if (balanceDao.decrementQuantity(balance.getId(), fact.quantity(), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return unit;
    }

    /**
     * 调拨**转入**：向目标仓累加并写 {@code TRANSFER_IN} 流水（方向 = 入）。
     *
     * <p>与采购入库（{@link #postPurchaseInbound}）同一取向：**入方向允许建零余额行**
     * —— 目标仓从没有过该 SKU 时，由本次调入建立该行，单位取明细行的快照
     * （= 发出时记录的源仓单位）。
     *
     * <p><b>单位必须与目标仓既有记账单位一致</b>：不一致直接 41044，绝不隐式换算
     * （Q13）。源仓按「箱」记账、目标仓按「kg」记账时把 10 箱加成 10 kg 会得到一个
     * 没有物理意义的余额，而错误只会在未来盘点时以「账实不符」的形式暴露。
     *
     * <p><b>转入成本取发出腿的事实，不取目标仓均价</b>：调拨只是把货换个仓库，总成本必须守恒
     * （见 {@link #transferInCost}）。均价按与采购入库相同的加权公式更新，因此
     * 「源仓 10 / 目标仓 8」时目标仓被拉向 10，而不是按 8 记；目标仓新建行时均价直接等于
     * 转入成本，不再出现「数量对、金额为零」。
     *
     * <p>转入没有下限判断（只增不减），但 {@code ck_inventory_balance_quantity} 仍然生效。
     */
    public void postTransferIn(InventoryTransferFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Transfer in requires the caller's transaction");
        }
        requireTransferFact(fact);
        if (fact.unitSnapshot() == null || fact.unitSnapshot().isBlank()) {
            // 转入必须知道期望单位（来自明细行快照），否则无法判断「是否在偷偷换算」。
            throw new ScmBusinessException(INVENTORY_TRANSFER_PARAM_INVALID);
        }

        warehouseService.require(fact.warehouseId());

        // 入方向允许首建余额行：单位取调拨明细的快照（= 源仓记账单位）。
        // 并发首建冲突后仍锁定同一余额行；SQL 冲突目标须匹配部分唯一索引（Q11）。
        balanceDao.insertOnConflictDoNothing(
                fact.warehouseId(), fact.skuId(), fact.unitSnapshot(), fact.operator());
        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }

        // 目标仓已有该 SKU 时，记账单位必须与调拨单位一致 —— 不做隐式换算。
        if (!fact.unitSnapshot().equals(balance.getUnit())) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_UNIT_MISMATCH);
        }

        BigDecimal before = balance.getQuantity();
        BigDecimal after = before.add(fact.quantity());
        BigDecimal transferredCost = transferInCost(fact.transferItemId());

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.TRANSFER_IN.name());
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.TRANSFER_IN_ITEM.name());
        movement.setSourceDocumentId(fact.transferId());
        movement.setSourceDocumentItemId(fact.transferItemId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(fact.unitSnapshot());
        movement.setUnitCost(transferredCost);
        movement.setBeforeQuantity(before);
        movement.setAfterQuantity(after);
        // 用**收货**时刻与收货人 —— 与转出的 occurred_at 是同一个调拨的两个不同时点，
        // 这正是两步式能被审计的关键：能看出货在途待了多久。
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (movementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_TRANSFER);
        }

        if (balanceDao.incrementQuantityAndSetAvgCost(balance.getId(), fact.quantity(),
                inboundAvgCost(balance, fact.quantity(), transferredCost), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 调拨转入的单位成本 = 同一条明细行**发出腿**当时的成本事实。
     *
     * <p>发出与收货是两个事务（两步式），收货时内存里什么都没有，只能回读已冻结的流水；
     * 不在调拨明细行上另存一份成本副本，否则同一事实有两处可各自漂移。
     *
     * <p>发出腿 {@code unit_cost} 为空只可能发生在成本核算（V34）上线前已发出、上线后才收货的
     * 在途单上 —— 那批货确实没有成本事实，按 0 计入，与 {@link #inboundAvgCost} 对无成本入库的
     * 同一取向：宁可在均价上体现「有一批货成本未知」，也不凭空编一个价格。
     *
     * <p>流水本身不会缺失：收货要求单据为 SHIPPED，而 {@code ship()} 与转出流水在同一事务内写入。
     * 真缺了就是账实不一致，必须响亮失败 —— 按 0 静默入账会把「成本清零」这个缺陷重新引进来。
     */
    private BigDecimal transferInCost(Long transferItemId) {
        InventoryMovementEntity shipped = movementDao.selectBySourceItem(
                ScmInventorySourceDocumentTypeEnum.TRANSFER_OUT_ITEM.name(), transferItemId);
        if (shipped == null) {
            throw new IllegalStateException(
                    "TRANSFER_IN without the matching TRANSFER_OUT movement, transfer item " + transferItemId);
        }
        return shipped.getUnitCost() == null ? BigDecimal.ZERO : shipped.getUnitCost();
    }

    /**
     * 规格转换**转出**：从**源 SKU** 扣减并写 {@code CONVERT_OUT} 流水（方向 = 出）。
     *
     * <p>与销售出库 / 调拨转出同一取向：**不建零余额行**（没有余额行 = 从未入库 = 无货可转），
     * 先判可用量再持锁扣减。
     *
     * <p><b>单位与出库 / 调拨的关键差异：单位来自单据声明，不是余额</b>。
     * 折算关系本身含单位（1 箱 = 10 kg），所以单据必须写清源单位；
     * 这里用它与余额记账单位**比对**，不一致直接失败（41059），不做隐式换算，
     * 也**不**按声明改写余额单位 —— 那会让既有余额的含义漂移。
     */
    public void postConvertOut(InventoryConversionFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Conversion out requires the caller's transaction");
        }
        requireConversionFact(fact);

        warehouseService.require(fact.warehouseId());

        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_SOURCE_BALANCE_MISSING);
        }
        if (!fact.unit().equals(balance.getUnit())) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_SOURCE_UNIT_MISMATCH);
        }

        BigDecimal onHand = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null
                ? BigDecimal.ZERO : balance.getReservedQuantity();
        if (onHand.subtract(reserved).compareTo(fact.quantity()) < 0) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_INSUFFICIENT_AVAILABLE);
        }
        BigDecimal after = onHand.subtract(fact.quantity());

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.CONVERT_OUT.name());
        // 方向编码在**来源类型**里：转出与转入引用同一条明细行，必须落在两个来源类型下，
        // 否则第二条流水会撞上 uk_inventory_movement_source_active（与调拨同一原因）。
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.CONVERT_OUT_ITEM.name());
        movement.setSourceDocumentId(fact.conversionId());
        movement.setSourceDocumentItemId(fact.conversionItemId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(fact.unit());
        // 基准由调用方在写任何腿之前按持锁快照给出：转出与转入两条腿必须同源，
        // 否则同一条明细的总成本不守恒（成本核算上线前这里曾留空）。
        movement.setUnitCost(fact.unitCost());
        movement.setBeforeQuantity(onHand);
        movement.setAfterQuantity(after);
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (movementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_CONVERSION);
        }
        if (balanceDao.decrementQuantity(balance.getId(), fact.quantity(), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 规格转换**转入**：向**目标 SKU** 累加并写 {@code CONVERT_IN} 流水（方向 = 入）。
     *
     * <p>与采购入库 / 调拨转入同一取向：**入方向允许建零余额行** —— 目标 SKU 在该仓库
     * 从没有过余额时，由本次转换建立，单位取单据声明的目标单位。
     *
     * <p>目标 SKU **已有**余额行时，其记账单位必须等于声明的目标单位（否则 41060）：
     * 否则会把「箱」与「kg」相加，得到一个没有物理意义的余额。
     *
     * <p><b>成本只平移、不凭空产生</b>：转入腿单价 = 转出腿总成本 ÷ 目标腿数量（由调用方按
     * 同一基准换算），目标 SKU 均价再按与采购入库相同的加权公式更新。因此「9.5 kg 拆成
     * 1 箱」两边金额相等，而目标 SKU 新建余额行时均价等于转入成本、不再是 0。
     */
    public void postConvertIn(InventoryConversionFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Conversion in requires the caller's transaction");
        }
        requireConversionFact(fact);

        warehouseService.require(fact.warehouseId());

        // 入方向允许首建余额行：单位取单据声明的目标单位。
        // 并发首建冲突后仍锁定同一余额行；SQL 冲突目标须匹配部分唯一索引（Q11）。
        balanceDao.insertOnConflictDoNothing(
                fact.warehouseId(), fact.skuId(), fact.unit(), fact.operator());
        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }
        if (!fact.unit().equals(balance.getUnit())) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_TARGET_UNIT_MISMATCH);
        }

        BigDecimal before = balance.getQuantity();
        BigDecimal after = before.add(fact.quantity());

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.CONVERT_IN.name());
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.CONVERT_IN_ITEM.name());
        movement.setSourceDocumentId(fact.conversionId());
        movement.setSourceDocumentItemId(fact.conversionItemId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(fact.unit());
        movement.setUnitCost(fact.unitCost());
        movement.setBeforeQuantity(before);
        movement.setAfterQuantity(after);
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (movementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_CONVERSION);
        }
        if (balanceDao.incrementQuantityAndSetAvgCost(balance.getId(), fact.quantity(),
                inboundAvgCost(balance, fact.quantity(), fact.unitCost()), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 查询可用量；缺少查询标识或余额不存在时返回零，不返回表示能力未启用的 {@code null}。
     *
     * <p>出库波次起 {@code reserved} 返回**真实预留量**（此前恒为零）。
     */
    public PurchaseInventoryContract.Availability queryAvailability(Long skuId, Long warehouseId) {
        if (skuId == null || warehouseId == null) {
            return new PurchaseInventoryContract.Availability(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        InventoryBalanceEntity balance = balanceDao.selectByWarehouseAndSku(warehouseId, skuId);
        if (balance == null) {
            return new PurchaseInventoryContract.Availability(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal reserved = balance.getReservedQuantity() == null
                ? BigDecimal.ZERO : balance.getReservedQuantity();
        return new PurchaseInventoryContract.Availability(balance.getQuantity(), reserved);
    }

    /**
     * 校验入库事实；数量口径、超收容差与实重来源由采购侧校验。
     */
    private static void requireFact(PurchaseInventoryContract.InboundFact fact) {
        if (fact == null
                || fact.warehouseId() == null
                || fact.skuId() == null
                || fact.receiptId() == null
                || fact.receiptItemId() == null
                || fact.quantity() == null
                || fact.quantity().signum() <= 0
                || fact.unit() == null
                || fact.unit().isBlank()
                || fact.occurredAt() == null
                || fact.operator() == null
                || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }
    }

    /**
     * 校验出库事实；可用量门槛在 {@link #postSalesOutbound} 持锁后判定，
     * 单位不在这里校验（以余额记账单位为准，见方法注释）。
     */
    private static void requireOutboundFact(InventoryOutboundFact fact) {
        if (fact == null
                || fact.warehouseId() == null
                || fact.skuId() == null
                || fact.outboundId() == null
                || fact.outboundItemId() == null
                || fact.quantity() == null
                || fact.quantity().signum() <= 0
                || fact.occurredAt() == null
                || fact.operator() == null
                || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_OUTBOUND_PARAM_INVALID);
        }
    }

    /**
     * 校验盘点事实。
     *
     * <p>两个数量都允许为 0（账面 0、实盘 0 都是合法事实），但**都不允许为负**：
     * 负的实盘量没有物理含义，负的账面量则意味着库本身已经坏了。
     * 差异的正负在这里不做判断 —— 那是 {@code delta} 的事，不是参数合法性的问题。
     */
    private static void requireStocktakeFact(InventoryStocktakeFact fact) {
        if (fact == null
                || fact.warehouseId() == null
                || fact.skuId() == null
                || fact.stocktakeId() == null
                || fact.stocktakeItemId() == null
                || fact.bookQuantity() == null
                || fact.bookQuantity().signum() < 0
                || fact.actualQuantity() == null
                || fact.actualQuantity().signum() < 0
                || fact.occurredAt() == null
                || fact.operator() == null
                || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_PARAM_INVALID);
        }
    }

    /**
     * 校验报损报溢事实。
     *
     * <p>数量必须**严格为正**：与盘点不同，报损报溢没有「零差异」这种合法事实 ——
     * 一张数量为 0 的报损单在业务上不存在，出现它只可能是录单错误或调用方拼错了参数。
     * 单据类型必须属于白名单（未知类型无法确定方向，不能猜）。
     */
    private static void requireLossGainFact(InventoryLossGainFact fact) {
        if (fact == null
                || fact.warehouseId() == null
                || fact.skuId() == null
                || fact.lossGainId() == null
                || fact.lossGainItemId() == null
                || fact.adjustType() == null
                || fact.adjustType().isBlank()
                || fact.quantity() == null
                || fact.quantity().signum() <= 0
                || fact.occurredAt() == null
                || fact.operator() == null
                || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_PARAM_INVALID);
        }
    }

    /**
     * 校验调拨事实。
     *
     * <p>数量必须**严格为正**（调拨没有「零数量」这种合法事实）。
     * {@code unitSnapshot} 刻意**不在这里校验**：发出时它应当为空（单位由源仓余额决定），
     * 收货时必须非空（期望单位来自明细行快照）—— 两个方向的要求相反，
     * 因此各自在自己的方法里判断，而不是塞进这个共用校验里。
     */
    private static void requireTransferFact(InventoryTransferFact fact) {
        if (fact == null
                || fact.warehouseId() == null
                || fact.skuId() == null
                || fact.transferId() == null
                || fact.transferItemId() == null
                || fact.quantity() == null
                || fact.quantity().signum() <= 0
                || fact.occurredAt() == null
                || fact.operator() == null
                || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_PARAM_INVALID);
        }
    }

    /**
     * 校验规格转换事实。
     *
     * <p>与其它事实不同的一点：**单位是必填的**（单据声明，不是从余额推导），
     * 所以这里校验它非空。转出与转入都要求非空，因此可以放在共用校验里
     * —— 这一点与调拨相反（调拨的转出不传单位，由余额决定）。
     */
    private static void requireConversionFact(InventoryConversionFact fact) {
        if (fact == null
                || fact.warehouseId() == null
                || fact.conversionId() == null
                || fact.conversionItemId() == null
                || fact.skuId() == null
                || fact.quantity() == null
                || fact.quantity().signum() <= 0
                || fact.unit() == null
                || fact.unit().isBlank()
                || fact.occurredAt() == null
                || fact.operator() == null
                || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_PARAM_INVALID);
        }
    }
}
