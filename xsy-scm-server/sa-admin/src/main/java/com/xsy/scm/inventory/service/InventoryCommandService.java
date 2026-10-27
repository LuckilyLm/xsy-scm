package com.xsy.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.inventory.constant.ScmInventoryLossGainTypeEnum;
import com.xsy.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import com.xsy.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import com.xsy.scm.inventory.dao.InventoryBalanceDao;
import com.xsy.scm.inventory.dao.InventoryMovementDao;
import com.xsy.scm.inventory.dao.InventoryLotBalanceDao;
import com.xsy.scm.inventory.dao.InventoryLotMovementDao;
import com.xsy.scm.inventory.domain.entity.InventoryLotBalanceEntity;
import com.xsy.scm.inventory.domain.entity.InventoryLotMovementEntity;
import com.xsy.scm.inventory.domain.InventoryConversionFact;
import com.xsy.scm.inventory.domain.InventoryLossGainFact;
import com.xsy.scm.inventory.domain.InventoryOutboundFact;
import com.xsy.scm.inventory.domain.InventoryPromotionGiftFact;
import com.xsy.scm.inventory.domain.InventoryStocktakeAdjustment;
import com.xsy.scm.inventory.domain.InventoryStocktakeFact;
import com.xsy.scm.inventory.domain.InventorySalesReturnFact;
import com.xsy.scm.inventory.domain.InventoryTransferFact;
import com.xsy.scm.inventory.domain.entity.InventoryBalanceEntity;
import com.xsy.scm.inventory.domain.entity.InventoryMovementEntity;
import com.xsy.scm.purchase.support.PurchaseInventoryContract;
import com.xsy.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_INSUFFICIENT_AVAILABLE;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_PARAM_INVALID;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_SOURCE_BALANCE_MISSING;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_SOURCE_UNIT_MISMATCH;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_CONVERSION_TARGET_UNIT_MISMATCH;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_CONVERSION;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_INBOUND;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_LOSS_GAIN;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_OUTBOUND;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_STOCKTAKE;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_TRANSFER;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_INSUFFICIENT_AVAILABLE;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_BALANCE_MISSING;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_BELOW_RESERVED;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_NEGATIVE_AFTER;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOSS_GAIN_PARAM_INVALID;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_PARAM_INVALID;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_PARAM_INVALID;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOT_ACTION_UNSUPPORTED;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOT_INSUFFICIENT;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOT_QUANTITY_INVALID;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_LOT_RETURN_SOURCE_MISSING;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_BALANCE_MISSING;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_BELOW_RESERVED;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_NEGATIVE_AFTER;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_PARAM_INVALID;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_INSUFFICIENT_AVAILABLE;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_PARAM_INVALID;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_SOURCE_BALANCE_MISSING;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_TRANSFER_UNIT_MISMATCH;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_UNIT_MISMATCH;

/**
 * 统一写入库存余额和流水，失败时随业务事务一起回滚。数量只能增减，不能直接覆盖；均价只在有明确入库成本时重算。先锁业务单据，再按仓库和商品规格的顺序锁余额。只有采购入库、销售退货、调拨转入、规格转换转入能首次建立余额记录。
 */
@Service
@RequiredArgsConstructor
public class InventoryCommandService {

    private final InventoryBalanceDao inventoryBalanceDao;

    private final InventoryMovementDao inventoryMovementDao;

    /**
     * 批次子账：与总账同事务双写，查询层不做二次计算。
     */
    private final InventoryLotBalanceDao inventoryLotBalanceDao;

    private final InventoryLotMovementDao inventoryLotMovementDao;

    private final WarehouseService warehouseService;

    /**
     * 追加采购入库流水并累加余额；数量必须为正且单位须与已有余额一致。来源行重复时抛错，不把重复记账当作幂等重放。
     */
    public void postPurchaseInbound(PurchaseInventoryContract.InboundFact fact) {
        // 不自行开启事务，防止脱离收货确认后分步提交。
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Purchase inbound requires the caller's transaction");
        }
        requireFact(fact);

        warehouseService.require(fact.warehouseId());

        // 并发首建冲突后仍锁定同一余额行；SQL 冲突目标须匹配部分唯一索引。
        inventoryBalanceDao.insertOnConflictDoNothing(fact.warehouseId(), fact.skuId(), fact.unit(), fact.operator());
        InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }

        // 一个仓库 + SKU 只允许一个记账单位，不做自动换算。
        if (!fact.unit().equals(balance.getUnit())) {
            throw new ScmBusinessException(INVENTORY_UNIT_MISMATCH);
        }

        // 持有余额行锁后读取期初数量，避免并发入库读到过期数据。
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

        if (inventoryMovementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_INBOUND);
        }

        // 均价（加权）由三条入库路径更新 —— 采购入库、调拨转入、规格转换转入，规则见类注释；
        // 本方法是其中的采购入库，按 (旧量·旧均价 + 入量·入价) / 新量重算。
        // 数量增量与均价写入合并成一条语句 —— 一次逻辑行变更只自增一次 version。
        // 报溢等没有成本数据的入库走普通的 incrementQuantity，均价不变；
        // 出库更不改均价，只把当时的均价写进流水。
        if (inventoryBalanceDao.incrementQuantityAndSetAvgCost(balance.getId(), fact.quantity(),
            inboundAvgCost(balance, fact.quantity(), fact.unitCost()), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        // 批次子账：总账写成功之后再逐批写子账，两者同一事务；未启用批次的仓库 lots 为空，行为不变。
        for (PurchaseInventoryContract.LotQuantity lot : fact.lots()) {
            if (lot.quantity().signum() <= 0) {
                throw new ScmBusinessException(INVENTORY_LOT_QUANTITY_INVALID);
            }
            postLotInboundLeg(fact, lot, balance.getUnit());
        }
    }

    /**
     * 采购入库的批次腿：先锁（或首建）批次余额，再追加只追加流水并累加数量。
     */
    private void postLotInboundLeg(PurchaseInventoryContract.InboundFact fact,
        PurchaseInventoryContract.LotQuantity lot, String unit) {
        inventoryLotBalanceDao.insertOnConflictDoNothing(fact.warehouseId(), fact.skuId(), lot.lotId(), unit,
            fact.operator());
        InventoryLotBalanceEntity lotBalance = inventoryLotBalanceDao.lockByWarehouseSkuLot(fact.warehouseId(),
            fact.skuId(), lot.lotId());
        if (lotBalance == null) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }
        if (!unit.equals(lotBalance.getUnit())) {
            throw new ScmBusinessException(INVENTORY_UNIT_MISMATCH);
        }
        BigDecimal before = lotBalance.getQuantity();
        BigDecimal after = before.add(lot.quantity());
        appendLotMovement(fact.warehouseId(), fact.skuId(), lot.lotId(),
            ScmInventoryMovementTypeEnum.PURCHASE_IN.name(),
            ScmInventorySourceDocumentTypeEnum.PURCHASE_RECEIPT_ITEM.name(), fact.receiptItemId(), fact.receiptItemId(),
            lot.quantity(), unit, before, after, fact.occurredAt(), fact.operator());
        if (inventoryLotBalanceDao.addQuantity(lotBalance.getId(), lot.quantity(), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 追加一条批次流水（只追加，禁止改写）。
     */
    private void appendLotMovement(Long warehouseId, Long skuId, Long lotId, String movementType,
        String sourceDocumentType, Long sourceDocumentId, Long sourceDocumentItemId, BigDecimal quantity, String unit,
        BigDecimal before, BigDecimal after, OffsetDateTime occurredAt, String operator) {
        InventoryLotMovementEntity movement = new InventoryLotMovementEntity();
        movement.setWarehouseId(warehouseId);
        movement.setSkuId(skuId);
        movement.setLotId(lotId);
        movement.setMovementType(movementType);
        movement.setSourceDocumentType(sourceDocumentType);
        movement.setSourceDocumentId(sourceDocumentId);
        movement.setSourceDocumentItemId(sourceDocumentItemId);
        movement.setQuantity(quantity);
        movement.setUnitSnapshot(unit);
        movement.setBeforeQuantity(before);
        movement.setAfterQuantity(after);
        movement.setOccurredAt(occurredAt);
        movement.setOperator(operator);
        movement.setCreatedBy(operator);
        inventoryLotMovementDao.insert(movement);
    }

    /**
     * 计算采购入库后的移动加权均价，不直接写库。必须使用已加锁的余额；均价与库存数量由同一条更新语句保存。
     */
    private static BigDecimal inboundAvgCost(InventoryBalanceEntity balance, BigDecimal inboundQuantity,
        BigDecimal inboundUnitCost) {
        return weightedAvgCost(balance.getQuantity(), balance.getAvgCost(), inboundQuantity, inboundUnitCost);
    }

    /**
     * 移动加权均价 —— 全项目唯一一份加权公式实现，转换的成本基准计算也走它。
     *
     * <pre>
     * newAvg = (旧量·旧均价 + 入量·入价) / (旧量 + 入量)     四舍五入到 4 位小数
     * </pre>
     *
     * <p>
     * 取整方式固定为 {@code HALF_UP}：四舍五入是财务上的默认做法，而 {@code HALF_EVEN}（银行家舍入）会让「同一批数据算两遍」的结果依赖于末位奇偶，对账时难以解释。
     *
     * <p>
     * 均价为空按 0 计入 —— 那意味着「这批货没有成本数据」。把它当作 0 会把均价拉低，这是有意为之：宁可在均价上体现「有一批货成本未知」，也不要凭空编一个价格。采购收货的 unit_cost
     * 一定非空；实际会走到空值分支的只有成本核算上线前已发出、上线后才收货的在途调拨。
     */
    public static BigDecimal weightedAvgCost(BigDecimal beforeQuantity, BigDecimal beforeAvgCost,
        BigDecimal inboundQuantity, BigDecimal inboundUnitCost) {
        BigDecimal oldQuantity = beforeQuantity == null ? BigDecimal.ZERO : beforeQuantity;
        BigDecimal oldAvg = beforeAvgCost == null ? BigDecimal.ZERO : beforeAvgCost;
        BigDecimal inCost = inboundUnitCost == null ? BigDecimal.ZERO : inboundUnitCost;
        // 负库存期间没有可加权的正数库存：这批入库就是新的成本起点，不能把负数量带进分母。
        if (oldQuantity.signum() <= 0) {
            return inCost;
        }
        BigDecimal newQuantity = oldQuantity.add(inboundQuantity);
        if (newQuantity.signum() <= 0) {
            // 入量恒为正，理论上不可达；保留原均价，避免除以零或负数。
            return oldAvg;
        }
        return oldQuantity.multiply(oldAvg).add(inboundQuantity.multiply(inCost)).divide(newQuantity, 4,
            RoundingMode.HALF_UP);
    }

    /**
     * 在任何库存写入之前，锁定本次动作涉及的余额行并记录期初数量与均价。
     *
     * <p>
     * 规格转换需要它有两个原因：① 加锁顺序 —— 调用方把明细拆成若干步后按 {@code (skuId, 先入后出)} 排序执行，这里按 {@code skuId} 升序把全部行一次锁齐，后续的
     * {@code FOR UPDATE} 只是重入本事务已持有的行锁；② 成本基准的期初输入 —— 同一条明细的转入可能先于转出执行，到了执行时再读均价会读到被本单前半段改过的值，两条记录于是不一致、总成本不守恒。链式转换（某
     * SKU 在本单内既进又出）的基准由调用方在这份期初数据之上计算（见 {@code InventoryConversionService#resolveOutboundCostBasis}）：执行顺序保证同一 SKU
     * 先入后出，所以它的转出在真实账本上看到的正是「进完之后」的均价。
     *
     * @param skuIds
     *            本单涉及的源与目标 SKU（重复与无序都可以，内部按升序去重锁定）
     * @return {@code skuId -> 期初数量与均价}；没有余额行的 SKU 不在结果里，调用方按 0 处理
     */
    public Map<Long, CostBasis> lockCostBasis(Long warehouseId, Collection<Long> skuIds) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Cost basis snapshot requires the caller's transaction");
        }
        Map<Long, CostBasis> basis = new HashMap<>();
        skuIds.stream().filter(Objects::nonNull).distinct().sorted().forEach(skuId -> {
            InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(warehouseId, skuId);
            // 没有余额行 = 从未入库 = 没有成本数据：出库会因此在后面失败（41058），
            // 新建余额的入库则以 0 起步，随后被本次转入的均价覆盖。
            if (balance != null) {
                basis.put(skuId, new CostBasis(balance.getQuantity(), balance.getAvgCost()));
            }
        });
        return basis;
    }

    /**
     * 一行余额的成本期初：数量与均价必须取自同一时刻（加权公式的两个自变量）。
     *
     * @param quantity
     *            持锁后读到的账面数量
     * @param avgCost
     *            持锁后读到的移动加权均价，可为空（按 0 计）
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
     * 转换后每个目标单位的成本 = 转出总成本 ÷ 转入数量（{@code HALF_UP} 到 4 位，与 {@code avg_cost} 的精度一致）。
     *
     * <p>
     * 折算率是人工声明的（一箱是 9.5 还是 10 kg 取决于供应商与批次），所以跨单位的单价必然不同 —— 守恒的是总成本，不是单价。
     */
    public static BigDecimal convertedUnitCost(BigDecimal sourceQuantity, BigDecimal sourceUnitCost,
        BigDecimal targetQuantity) {
        if (targetQuantity.signum() <= 0) {
            throw new IllegalArgumentException("Conversion target quantity must be positive: " + targetQuantity);
        }
        return sourceQuantity.multiply(sourceUnitCost).divide(targetQuantity, 4, RoundingMode.HALF_UP);
    }

    /**
     * 追加销售出库流水并扣减余额，必须参与调用方事务。
     *
     * <p>
     * 与入库的三点差异：
     * <ol>
     * <li>不建零余额行：出库时若该 (仓库, SKU) 没有余额行，说明从来没有入过库，直接判可用量不足（41004），而不是先建一行 0 再扣成负数；</li>
     * <li>可用量限制：{@code 可用量 = quantity - reserved_quantity}，出库不得吃掉已预留的货（否则数据库的 {@code ck_inventory_balance_available}
     * 会拒绝）；</li>
     * <li>方向：{@code after = before - quantity}，与 {@code ck_inventory_movement_snap} 的出库分支一致。</li>
     * </ol>
     *
     * <p>
     * 单位由余额决定，不由调用方传入：{@code (warehouse_id, sku_id)} 的记账单位以余额为准。让调用方传一个单位再与余额比对，等于要求调用方先查一次余额，
     * 既多一次查询、又把「谁是准的」搞反。这里直接取余额单位写入流水，并把该单位返回给调用方回写单据行。
     *
     * @return 本次出库使用的记账单位（调用方用于回写 {@code unitSnapshot}）
     */
    public String postSalesOutbound(InventoryOutboundFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Sales outbound requires the caller's transaction");
        }
        requireOutboundFact(fact);

        var warehouse = warehouseService.require(fact.warehouseId());
        boolean allowNegative = Boolean.TRUE.equals(warehouse.getAllowNegativeStock());

        // 未开启负库存时，没有余额行 = 从未入库 = 无货可出。
        // 开启后允许先发货后补入库：用出库行的记账单位建零余额行，再扣成负数。
        InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            if (!allowNegative || fact.stockUnit() == null) {
                throw new ScmBusinessException(INVENTORY_INSUFFICIENT_AVAILABLE);
            }
            inventoryBalanceDao.insertOnConflictDoNothing(fact.warehouseId(), fact.skuId(), fact.stockUnit(),
                fact.operator());
            balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
            if (balance == null || !fact.stockUnit().equals(balance.getUnit())) {
                throw new ScmBusinessException(INVENTORY_UNIT_MISMATCH);
            }
        }

        String unit = balance.getUnit();

        // 持有行锁后再算可用量，避免并发下读到过期数据。
        BigDecimal onHand = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null ? BigDecimal.ZERO : balance.getReservedQuantity();
        BigDecimal available = onHand.subtract(reserved);
        // 负库存只放行“没有预留”的余额；已有预留的货仍不能被本单吃掉，否则会把别人的库存占成负数。
        if (available.compareTo(fact.quantity()) < 0 && (!allowNegative || reserved.signum() > 0)) {
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
        // 成本：出库按当时的移动加权均价记成本（此前留空）。
        // 出库不改变均价，所以余额不动，只把成本写进流水 —— 这是成本核算的核心变化。
        movement.setUnitCost(balance.getAvgCost());
        movement.setBeforeQuantity(onHand);
        movement.setAfterQuantity(after);
        // 与入库同一要求：用出库确认时刻与确认人，不能改用当前时间或当前登录人。
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (inventoryMovementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_OUTBOUND);
        }

        if (inventoryBalanceDao.decrementQuantity(balance.getId(), fact.quantity(), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        // 批次子账：按先进先出分摊，逐批写负向流水；未启用批次的仓库没有子账行，自然跳过。
        postLotOutboundLeg(fact.warehouseId(), fact.skuId(), unit, fact.quantity(),
            ScmInventoryMovementTypeEnum.SALES_OUT.name(),
            ScmInventorySourceDocumentTypeEnum.SALES_OUTBOUND_ITEM.name(), fact.outboundId(), fact.outboundItemId(),
            fact.occurredAt(), fact.operator());
        return unit;
    }

    /**
     * 赠品出库按已确定的赠品权益记账，使用赠品权益 ID 作为防重来源，不能虚造订单明细。必须持有余额锁并校验可用库存，单位成本取出库时的移动均价。
     */
    public String postPromotionGiftOutbound(InventoryPromotionGiftFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Promotion gift outbound requires the caller's transaction");
        }
        requirePromotionGiftFact(fact);

        warehouseService.require(fact.warehouseId());

        // 与销售出库同样：没有余额行 = 从未入库 = 无货可出。
        InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_INSUFFICIENT_AVAILABLE);
        }

        String unit = balance.getUnit();
        BigDecimal onHand = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null ? BigDecimal.ZERO : balance.getReservedQuantity();
        // 持有行锁后再算可用量：赠品同样不能吃掉别人预留的货。
        if (onHand.subtract(reserved).compareTo(fact.quantity()) < 0) {
            throw new ScmBusinessException(INVENTORY_INSUFFICIENT_AVAILABLE);
        }

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.PROMOTION_GIFT_OUT.name());
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.ORDER_PROMOTION_GIFT.name());
        // 头级只做发车数据的溯源；防重锚点是 item 级的赠品权益 id。
        movement.setSourceDocumentId(fact.routeId());
        movement.setSourceDocumentItemId(fact.giftId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(unit);
        // 与销售出库同一规则：出库按当时的移动加权均价记成本，出库不改变均价。
        movement.setUnitCost(balance.getAvgCost());
        movement.setBeforeQuantity(onHand);
        movement.setAfterQuantity(onHand.subtract(fact.quantity()));
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (inventoryMovementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_OUTBOUND);
        }

        if (inventoryBalanceDao.decrementQuantity(balance.getId(), fact.quantity(), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        postLotOutboundLeg(fact.warehouseId(), fact.skuId(), unit, fact.quantity(),
            ScmInventoryMovementTypeEnum.PROMOTION_GIFT_OUT.name(),
            ScmInventorySourceDocumentTypeEnum.SALES_OUTBOUND_ITEM.name(), fact.routeId(), fact.giftId(),
            fact.occurredAt(), fact.operator());
        return unit;
    }

    public void postSalesReturnInbound(InventorySalesReturnFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Sales return inbound requires the caller's transaction");
        }
        if (fact == null || fact.warehouseId() == null || fact.skuId() == null || fact.returnId() == null
            || fact.returnReceiptItemId() == null || fact.quantity() == null || fact.quantity().signum() <= 0
            || fact.unit() == null || fact.unit().isBlank() || fact.unitCost() == null || fact.unitCost().signum() < 0
            || fact.occurredAt() == null || fact.operator() == null || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }
        warehouseService.require(fact.warehouseId());
        inventoryBalanceDao.insertOnConflictDoNothing(fact.warehouseId(), fact.skuId(), fact.unit(), fact.operator());
        InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }
        if (!fact.unit().equals(balance.getUnit())) {
            throw new ScmBusinessException(INVENTORY_UNIT_MISMATCH);
        }
        BigDecimal before = balance.getQuantity();
        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.SALES_RETURN_IN.name());
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.SALES_RETURN_RECEIPT_ITEM.name());
        movement.setSourceDocumentId(fact.returnId());
        movement.setSourceDocumentItemId(fact.returnReceiptItemId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(fact.unit());
        movement.setUnitCost(fact.unitCost());
        movement.setBeforeQuantity(before);
        movement.setAfterQuantity(before.add(fact.quantity()));
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());
        if (inventoryMovementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_INBOUND);
        }
        if (inventoryBalanceDao.incrementQuantityAndSetAvgCost(balance.getId(), fact.quantity(),
            inboundAvgCost(balance, fact.quantity(), fact.unitCost()), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        postLotReturnLeg(fact, balance.getUnit());
    }

    /**
     * 出库的批次腿：按批次先后（先进先出）分摊，逐批追加负向流水。
     *
     * <p>
     * 没有批次余额行说明该仓库未启用批次追踪（或库存来自批次之前的历史数据），此时不写子账；启用批次却分摊不满则直接失败，绝不让总账与子账在静默中漂移。
     */
    private void postLotOutboundLeg(Long warehouseId, Long skuId, String unit, BigDecimal quantity, String movementType,
        String sourceDocumentType, Long sourceDocumentId, Long sourceDocumentItemId, OffsetDateTime occurredAt,
        String operator) {
        List<InventoryLotBalanceEntity> lotBalances = inventoryLotBalanceDao.lockPositiveByWarehouseSku(warehouseId,
            skuId);
        if (lotBalances.isEmpty()) {
            if (Boolean.TRUE.equals(warehouseService.require(warehouseId).getLotTrackingEnabled())) {
                throw new ScmBusinessException(INVENTORY_LOT_INSUFFICIENT);
            }
            return;
        }
        BigDecimal remaining = quantity;
        for (InventoryLotBalanceEntity lotBalance : lotBalances) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = lotBalance.getQuantity().min(remaining);
            if (take.signum() <= 0) {
                continue;
            }
            BigDecimal before = lotBalance.getQuantity();
            appendLotMovement(warehouseId, skuId, lotBalance.getLotId(), movementType, sourceDocumentType,
                sourceDocumentId, sourceDocumentItemId, take.negate(), unit, before, before.subtract(take), occurredAt,
                operator);
            if (inventoryLotBalanceDao.addQuantity(lotBalance.getId(), take.negate(), operator) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw new ScmBusinessException(INVENTORY_LOT_INSUFFICIENT);
        }
    }

    /**
     * 退货的批次腿：退回原出库批次。
     *
     * <p>
     * 按来源出库行的批次净额判断每个批次还能退回多少，多次部分退货不会把同一批次重复填满；找不到原出库批次时直接失败（41072），不猜批次、不新建批次。
     */
    private void postLotReturnLeg(InventorySalesReturnFact fact, String unit) {
        if (!Boolean.TRUE.equals(warehouseService.require(fact.warehouseId()).getLotTrackingEnabled())) {
            return;
        }
        if (fact.sourceOutboundItemId() == null) {
            throw new ScmBusinessException(INVENTORY_LOT_RETURN_SOURCE_MISSING);
        }
        // 净额只认「该出库行的出库流水」与「它的退回流水」：不同单据表的行 id 可能相同，类型收窄防止混入
        List<InventoryLotMovementDao.LotSourceNet> nets = inventoryLotMovementDao.sumByLotForSource(fact.warehouseId(),
            fact.skuId(), fact.sourceOutboundItemId(),
            List.of(ScmInventorySourceDocumentTypeEnum.SALES_OUTBOUND_ITEM.name(),
                ScmInventorySourceDocumentTypeEnum.SALES_RETURN_RECEIPT_ITEM.name()));
        if (nets.isEmpty()) {
            throw new ScmBusinessException(INVENTORY_LOT_RETURN_SOURCE_MISSING);
        }
        BigDecimal remaining = fact.quantity();
        for (InventoryLotMovementDao.LotSourceNet net : nets) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal stillOut = net.getNetQuantity().negate();
            if (stillOut.signum() <= 0) {
                continue;
            }
            BigDecimal take = stillOut.min(remaining);
            inventoryLotBalanceDao.insertOnConflictDoNothing(fact.warehouseId(), fact.skuId(), net.getLotId(), unit,
                fact.operator());
            InventoryLotBalanceEntity lotBalance = inventoryLotBalanceDao.lockByWarehouseSkuLot(fact.warehouseId(),
                fact.skuId(), net.getLotId());
            if (lotBalance == null) {
                throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
            }
            BigDecimal before = lotBalance.getQuantity();
            appendLotMovement(fact.warehouseId(), fact.skuId(), net.getLotId(),
                ScmInventoryMovementTypeEnum.SALES_RETURN_IN.name(),
                ScmInventorySourceDocumentTypeEnum.SALES_RETURN_RECEIPT_ITEM.name(), fact.returnId(),
                fact.sourceOutboundItemId(), take, unit, before, before.add(take), fact.occurredAt(), fact.operator());
            if (inventoryLotBalanceDao.addQuantity(lotBalance.getId(), take, fact.operator()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw new ScmBusinessException(INVENTORY_LOT_RETURN_SOURCE_MISSING);
        }
    }

    /**
     * 批次仓守卫：一期只支持采购入库与销售出库两种批次动作，其余库存动作直接拒绝。
     */
    private void requireLotTrackingDisabled(Long warehouseId) {
        if (Boolean.TRUE.equals(warehouseService.require(warehouseId).getLotTrackingEnabled())) {
            throw new ScmBusinessException(INVENTORY_LOT_ACTION_UNSUPPORTED);
        }
    }

    /**
     * 按盘点结果调整余额并追加盘盈 / 盘亏流水，必须参与调用方事务。
     *
     * <p>
     * 差异施加到当前账面量，而不是直接覆盖：
     *
     * <pre>
     * delta = actualQuantity - bookQuantity   // 清点发现的差异，基线是保存草稿时记录的数量
     * after = live + delta                    // live 是此刻持锁读到的账面量
     * </pre>
     * <p>
     * 之所以不直接把账面量改写成实盘数：保存草稿到确认之间很可能发生了收货 / 出库（鲜活品仓里这是常态），直接改写会把那些真实变动悄悄抹掉，而抹掉的痕迹在任何报表里都看不出来。用「施加差异」的写法，期间发生的变动被完整保留，
     * 且当期间无变动时 {@code after} 恰好等于实盘数 —— 符合直觉。
     *
     * <p>
     * delta 为 0 时不写流水：{@code quantity} 恒为正（{@code ck_inventory_movement_qty}），写不出「零差异」的流水；强行写一条 0
     * 会让「一行一流水」的防重索引语义变脏。调用方据 {@code movementWritten} 区分「调整了 0」与「无需调整」。
     *
     * <p>
     * 不建零余额行：记账单位只能来自余额行，因此从未入库过的 SKU 不能在盘点里凭空盘盈 —— 那需要先有入库记录来确定单位（41023）。
     *
     * @return 本次调整的结果（记账单位 + 差异 + 调整前后量 + 是否写了流水）
     */
    public InventoryStocktakeAdjustment postStocktakeAdjust(InventoryStocktakeFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Stocktake adjustment requires the caller's transaction");
        }
        requireStocktakeFact(fact);

        warehouseService.require(fact.warehouseId());
        requireLotTrackingDisabled(fact.warehouseId());

        // 盘点不建行：没有余额行 = 从未入库 = 无账可盘，也无法确定记账单位。
        InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_BALANCE_MISSING);
        }

        String unit = balance.getUnit();

        // 持有行锁后再读账面量与预留量，避免并发下拿到过期数据。
        BigDecimal live = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null ? BigDecimal.ZERO : balance.getReservedQuantity();

        BigDecimal delta = fact.actualQuantity().subtract(fact.bookQuantity());
        BigDecimal after = live.add(delta);

        // 盘亏不得把库存推成负数。数据库的 ck_inventory_balance_quantity 也会拦，
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
        // unit_cost 记的是本笔移动当时的余额均价，盘盈与盘亏都写，不留空。
        // （「盘点没有成本依据、unit_cost 留空」是移动加权平均上线前的旧规则；
        // ck_inventory_movement_cost 允许 NULL 只是容忍历史行，不代表新记录该写空。）
        movement.setUnitCost(balance.getAvgCost());
        movement.setBeforeQuantity(live);
        movement.setAfterQuantity(after);
        // 与入库/出库同一要求：用盘点确认时刻与确认人，不能改用当前时间或当前登录人。
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (inventoryMovementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_STOCKTAKE);
        }

        int rows = gain
            ? inventoryBalanceDao.incrementQuantity(balance.getId(), quantity, fact.operator())
            : inventoryBalanceDao.decrementQuantity(balance.getId(), quantity, fact.operator());
        if (rows != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        return new InventoryStocktakeAdjustment(unit, delta, live, after, true);
    }

    /**
     * 按报损 / 报溢审批结果调整余额并追加流水，必须参与调用方事务。
     *
     * <p>
     * 方向由单据类型决定，不由数量正负决定：{@code LOSS} → {@code LOSS_REPORT} 且 {@code after = before - quantity}；{@code OVERFLOW} →
     * {@code GAIN_REPORT} 且 {@code after = before + quantity}。数量恒为正（{@code ck_inventory_movement_qty}），方向只存在于
     * {@code movement_type} 里 —— 与 {@code ck_inventory_movement_snap} 的分组一致。
     *
     * <p>
     * 报损有两条下限，报溢没有上限：
     * <ul>
     * <li>库存下限：{@code after >= 0}，报损不得把库存推成负数（41033）；</li>
     * <li>可用量：{@code after >= reserved}，报损不得吃掉已预留的货（41034）—— 预留代表对下游（销售订单）的承诺，报损不能单方面让它落空。</li>
     * </ul>
     * 报溢只增不减，因此不需要下限判断；但它不建零余额行：记账单位只能来自余额行，从未入库过的 SKU 报 41032（与盘点同一约束）。
     *
     * <p>
     * 用审核时刻与审核人，不是创建时刻与创建人：库存是在审核那一刻变的，流水的 {@code occurred_at} 必须指向那个时刻，否则「按业务时间查流水」会失真。
     *
     * @return 本次使用的记账单位（调用方用于回写明细行的 {@code unitSnapshot}）
     */
    public String postLossGainAdjust(InventoryLossGainFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Loss/gain adjustment requires the caller's transaction");
        }
        requireLossGainFact(fact);

        // 未知类型直接失败：数据库 CHECK 兜底，但这里先给出可归因的错误码。
        ScmInventoryLossGainTypeEnum type = ScmInventoryLossGainTypeEnum.of(fact.adjustType());
        if (type == null) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_PARAM_INVALID);
        }

        warehouseService.require(fact.warehouseId());
        requireLotTrackingDisabled(fact.warehouseId());

        // 不建零余额行：没有余额行 = 从未入库 = 既无账可调，也无法确定记账单位。
        InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_BALANCE_MISSING);
        }

        String unit = balance.getUnit();

        // 持有行锁后再读账面量与预留量，避免并发下拿到过期快照。
        BigDecimal live = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null ? BigDecimal.ZERO : balance.getReservedQuantity();

        BigDecimal after;
        if (type.isInbound()) {
            after = live.add(fact.quantity());
        } else {
            after = live.subtract(fact.quantity());
            // 报损不得把库存推成负数。
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
        // unit_cost 记的是本笔移动<b>当时</b>的余额均价，报损与报溢两条腿都写，不留空。
        // 「报损报溢没有成本依据、留 NULL」是移动加权平均上线前的旧语义；
        // 出方向因「出库不改变均价」，事后再读余额拿到的仍是同一个值。
        movement.setUnitCost(balance.getAvgCost());
        movement.setBeforeQuantity(live);
        movement.setAfterQuantity(after);
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (inventoryMovementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_LOSS_GAIN);
        }

        int rows = type.isInbound()
            ? inventoryBalanceDao.incrementQuantity(balance.getId(), fact.quantity(), fact.operator())
            : inventoryBalanceDao.decrementQuantity(balance.getId(), fact.quantity(), fact.operator());
        if (rows != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return unit;
    }

    /**
     * 调拨转出：从源仓扣减并写 {@code TRANSFER_OUT} 流水（方向 = 出）。
     *
     * <p>
     * 与销售出库（{@link #postSalesOutbound}）做法一致：不建零余额行（没有余额行 = 从未入库 = 无货可调），先判可用量，再持锁扣减。可用量 =
     * {@code quantity − reserved_quantity}：调拨不得吃掉源仓已预留的货。
     *
     * <p>
     * 单位由源仓余额决定，不由调用方传入：直接取余额单位写入流水，并返回给调用方回写到明细行 —— 那正是收货时用来核对目标仓单位一致的那个值。
     *
     * @return 本次转出使用的记账单位（= 源仓记账单位）
     */
    public String postTransferOut(InventoryTransferFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Transfer out requires the caller's transaction");
        }
        requireTransferFact(fact);

        warehouseService.require(fact.warehouseId());
        requireLotTrackingDisabled(fact.warehouseId());

        // 转出不建行：没有余额行 = 从未入库 = 无货可调。
        InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_SOURCE_BALANCE_MISSING);
        }

        String unit = balance.getUnit();

        // 持有行锁后再算可用量，避免并发下读到过期数据。
        BigDecimal onHand = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null ? BigDecimal.ZERO : balance.getReservedQuantity();
        if (onHand.subtract(reserved).compareTo(fact.quantity()) < 0) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_INSUFFICIENT_AVAILABLE);
        }

        BigDecimal after = onHand.subtract(fact.quantity());

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.TRANSFER_OUT.name());
        // 方向编码在来源类型里：转出与转入引用同一条明细行，必须落在两个来源类型下，
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
        // 用发出时刻与发出人（不是当前时间 / 当前登录人）。
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (inventoryMovementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_TRANSFER);
        }

        if (inventoryBalanceDao.decrementQuantity(balance.getId(), fact.quantity(), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return unit;
    }

    /**
     * 调拨收货向目标仓增加库存并写 TRANSFER_IN 流水。允许为新商品建立余额行，但单位必须与已存在的余额一致（否则 41044）。转入成本取原发出流水，目标仓均价按移动加权计算，总成本不能凭空变化。
     */
    public void postTransferIn(InventoryTransferFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Transfer in requires the caller's transaction");
        }
        requireTransferFact(fact);
        if (fact.unitSnapshot() == null || fact.unitSnapshot().isBlank()) {
            // 转入必须知道期望单位（来自明细行记录），否则无法判断「是否在偷偷换算」。
            throw new ScmBusinessException(INVENTORY_TRANSFER_PARAM_INVALID);
        }

        warehouseService.require(fact.warehouseId());
        requireLotTrackingDisabled(fact.warehouseId());

        // 入方向允许首建余额行：单位取调拨明细中记录的单位（= 源仓记账单位）。
        // 并发首建冲突后仍锁定同一余额行；SQL 冲突目标须匹配部分唯一索引。
        inventoryBalanceDao.insertOnConflictDoNothing(fact.warehouseId(), fact.skuId(), fact.unitSnapshot(),
            fact.operator());
        InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }

        // 目标仓已有该 SKU 时，记账单位必须与调拨单位一致 —— 不做自动换算。
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
        // 用收货时刻与收货人 —— 与转出的 occurred_at 是同一个调拨的两个不同时点，
        // 这正是两步式能被审计的关键：能看出货在途待了多久。
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (inventoryMovementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_TRANSFER);
        }

        if (inventoryBalanceDao.incrementQuantityAndSetAvgCost(balance.getId(), fact.quantity(),
            inboundAvgCost(balance, fact.quantity(), transferredCost), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 调拨收货的单位成本必须读取对应发出流水，不以目标仓当前均价代替。老数据缺成本时按零成本参与均价，但缺少发出流水属于账务异常，必须报错。
     */
    private BigDecimal transferInCost(Long transferItemId) {
        InventoryMovementEntity shipped = inventoryMovementDao
            .selectBySourceItem(ScmInventorySourceDocumentTypeEnum.TRANSFER_OUT_ITEM.name(), transferItemId);
        if (shipped == null) {
            throw new IllegalStateException(
                "TRANSFER_IN without the matching TRANSFER_OUT movement, transfer item " + transferItemId);
        }
        return shipped.getUnitCost() == null ? BigDecimal.ZERO : shipped.getUnitCost();
    }

    /**
     * 转换出库必须有现存余额，不能为出方向新建零余额行。先锁余额并校验可用量，转出成本按源规格移动均价记录，作为转入成本的依据。
     */
    public void postConvertOut(InventoryConversionFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Conversion out requires the caller's transaction");
        }
        requireConversionFact(fact);

        warehouseService.require(fact.warehouseId());
        requireLotTrackingDisabled(fact.warehouseId());

        InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_SOURCE_BALANCE_MISSING);
        }
        if (!fact.unit().equals(balance.getUnit())) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_SOURCE_UNIT_MISMATCH);
        }

        BigDecimal onHand = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null ? BigDecimal.ZERO : balance.getReservedQuantity();
        if (onHand.subtract(reserved).compareTo(fact.quantity()) < 0) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_INSUFFICIENT_AVAILABLE);
        }
        BigDecimal after = onHand.subtract(fact.quantity());

        InventoryMovementEntity movement = new InventoryMovementEntity();
        movement.setWarehouseId(fact.warehouseId());
        movement.setSkuId(fact.skuId());
        movement.setMovementType(ScmInventoryMovementTypeEnum.CONVERT_OUT.name());
        // 方向编码在来源类型里：转出与转入引用同一条明细行，必须落在两个来源类型下，
        // 否则第二条流水会撞上 uk_inventory_movement_source_active（与调拨同一原因）。
        movement.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.CONVERT_OUT_ITEM.name());
        movement.setSourceDocumentId(fact.conversionId());
        movement.setSourceDocumentItemId(fact.conversionItemId());
        movement.setQuantity(fact.quantity());
        movement.setUnitSnapshot(fact.unit());
        // 基准由调用方在写入之前按持锁时读到的数据给出：转出与转入必须取自同一时刻，
        // 否则同一条明细的总成本不守恒（成本核算上线前这里曾留空）。
        movement.setUnitCost(fact.unitCost());
        movement.setBeforeQuantity(onHand);
        movement.setAfterQuantity(after);
        movement.setOccurredAt(fact.occurredAt());
        movement.setOperator(fact.operator());
        movement.setDeleted(false);
        movement.setCreatedBy(fact.operator());

        if (inventoryMovementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_CONVERSION);
        }
        if (inventoryBalanceDao.decrementQuantity(balance.getId(), fact.quantity(), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 转换入库可为目标规格新建余额，已有余额时单位必须一致（否则 41060）。转入总成本等于转出总成本，按转换数量折算单价，并参与目标规格的移动加权均价计算。
     */
    public void postConvertIn(InventoryConversionFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Conversion in requires the caller's transaction");
        }
        requireConversionFact(fact);

        warehouseService.require(fact.warehouseId());

        requireLotTrackingDisabled(fact.warehouseId());

        // 入方向允许首建余额行：单位取单据声明的目标单位。
        // 并发首建冲突后仍锁定同一余额行；SQL 冲突目标须匹配部分唯一索引。
        inventoryBalanceDao.insertOnConflictDoNothing(fact.warehouseId(), fact.skuId(), fact.unit(), fact.operator());
        InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
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

        if (inventoryMovementDao.insertOnConflictDoNothing(movement) != 1) {
            throw new ScmBusinessException(INVENTORY_DUPLICATE_CONVERSION);
        }
        if (inventoryBalanceDao.incrementQuantityAndSetAvgCost(balance.getId(), fact.quantity(),
            inboundAvgCost(balance, fact.quantity(), fact.unitCost()), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 查询可用量；缺少查询标识或余额不存在时返回零，不返回表示能力未启用的 {@code null}。
     *
     * <p>
     * {@code reserved} 返回当前真实预留量。
     */
    public PurchaseInventoryContract.Availability queryAvailability(Long skuId, Long warehouseId) {
        if (skuId == null || warehouseId == null) {
            return new PurchaseInventoryContract.Availability(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        InventoryBalanceEntity balance = inventoryBalanceDao.selectByWarehouseAndSku(warehouseId, skuId);
        if (balance == null) {
            return new PurchaseInventoryContract.Availability(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal reserved = balance.getReservedQuantity() == null ? BigDecimal.ZERO : balance.getReservedQuantity();
        return new PurchaseInventoryContract.Availability(balance.getQuantity(), reserved);
    }

    /**
     * 校验入库数据；数量规则、超收容差与实重来源由采购侧校验。
     */
    private static void requireFact(PurchaseInventoryContract.InboundFact fact) {
        if (fact == null || fact.warehouseId() == null || fact.skuId() == null || fact.receiptId() == null
            || fact.receiptItemId() == null || fact.quantity() == null || fact.quantity().signum() <= 0
            || fact.unit() == null || fact.unit().isBlank() || fact.occurredAt() == null || fact.operator() == null
            || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_PARAM_INVALID);
        }
    }

    /**
     * 校验出库数据；可用量限制在 {@link #postSalesOutbound} 持锁后判定，单位不在这里校验（以余额记账单位为准，见方法注释）。
     */
    private static void requireOutboundFact(InventoryOutboundFact fact) {
        if (fact == null || fact.warehouseId() == null || fact.skuId() == null || fact.outboundId() == null
            || fact.outboundItemId() == null || fact.quantity() == null || fact.quantity().signum() <= 0
            || fact.occurredAt() == null || fact.operator() == null || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_OUTBOUND_PARAM_INVALID);
        }
    }

    private static void requirePromotionGiftFact(InventoryPromotionGiftFact fact) {
        if (fact == null || fact.warehouseId() == null || fact.skuId() == null || fact.routeId() == null
            || fact.giftId() == null || fact.quantity() == null || fact.quantity().signum() <= 0
            || fact.occurredAt() == null || fact.operator() == null || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_OUTBOUND_PARAM_INVALID);
        }
    }

    /**
     * 校验盘点数据。
     *
     * <p>
     * 两个数量都允许为 0（账面 0、实盘 0 都合法），但都不允许为负：负的实盘量没有物理含义，负的账面量则意味着库本身已经坏了。差异的正负在这里不做判断 —— 那是 {@code delta} 的事，不是参数合法性的问题。
     */
    private static void requireStocktakeFact(InventoryStocktakeFact fact) {
        if (fact == null || fact.warehouseId() == null || fact.skuId() == null || fact.stocktakeId() == null
            || fact.stocktakeItemId() == null || fact.bookQuantity() == null || fact.bookQuantity().signum() < 0
            || fact.actualQuantity() == null || fact.actualQuantity().signum() < 0 || fact.occurredAt() == null
            || fact.operator() == null || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_STOCKTAKE_PARAM_INVALID);
        }
    }

    /**
     * 校验报损报溢数据。
     *
     * <p>
     * 数量必须严格为正：与盘点不同，报损报溢没有「零差异」这种合法情况 —— 一张数量为 0 的报损单在业务上不存在，出现它只可能是录单错误或调用方拼错了参数。单据类型必须是已知值（未知类型无法确定方向，不能猜）。
     */
    private static void requireLossGainFact(InventoryLossGainFact fact) {
        if (fact == null || fact.warehouseId() == null || fact.skuId() == null || fact.lossGainId() == null
            || fact.lossGainItemId() == null || fact.adjustType() == null || fact.adjustType().isBlank()
            || fact.quantity() == null || fact.quantity().signum() <= 0 || fact.occurredAt() == null
            || fact.operator() == null || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_LOSS_GAIN_PARAM_INVALID);
        }
    }

    /**
     * 校验调拨数据。
     *
     * <p>
     * 数量必须严格为正（调拨没有「零数量」这种合法情况）。{@code unitSnapshot} 刻意不在这里校验：发出时它应当为空（单位由源仓余额决定），收货时必须非空（期望单位来自明细行记录）——
     * 两个方向的要求相反，因此各自在自己的方法里判断，而不是塞进这个共用校验里。
     */
    private static void requireTransferFact(InventoryTransferFact fact) {
        if (fact == null || fact.warehouseId() == null || fact.skuId() == null || fact.transferId() == null
            || fact.transferItemId() == null || fact.quantity() == null || fact.quantity().signum() <= 0
            || fact.occurredAt() == null || fact.operator() == null || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_TRANSFER_PARAM_INVALID);
        }
    }

    /**
     * 校验规格转换数据。
     *
     * <p>
     * 与其它动作不同的一点：单位是必填的（单据声明，不是从余额推导），所以这里校验它非空。转出与转入都要求非空，因此可以放在共用校验里 —— 这一点与调拨相反（调拨的转出不传单位，由余额决定）。
     */
    private static void requireConversionFact(InventoryConversionFact fact) {
        if (fact == null || fact.warehouseId() == null || fact.conversionId() == null || fact.conversionItemId() == null
            || fact.skuId() == null || fact.quantity() == null || fact.quantity().signum() <= 0 || fact.unit() == null
            || fact.unit().isBlank() || fact.occurredAt() == null || fact.operator() == null
            || fact.operator().isBlank()) {
            throw new ScmBusinessException(INVENTORY_CONVERSION_PARAM_INVALID);
        }
    }
}
