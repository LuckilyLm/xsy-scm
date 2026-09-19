package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryMovementDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryOutboundFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryStocktakeAdjustment;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryStocktakeFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryMovementEntity;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_INBOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_OUTBOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_DUPLICATE_STOCKTAKE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_INSUFFICIENT_AVAILABLE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_BALANCE_MISSING;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_BELOW_RESERVED;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_NEGATIVE_AFTER;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_STOCKTAKE_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_UNIT_MISMATCH;

/**
 * 将已确认的采购收货行记入库存，必须参与调用方事务，失败时随收货确认一起回滚。
 *
 * <p>调用方须先持有采购/收货锁，再按 {@code (warehouseId, skuId)} 升序写入库存，
 * 避免多行收货以相反顺序获取余额锁。
 *
 * <p><b>本类是全仓唯一会改变 {@code inventory_balance.quantity} 的地方</b>，
 * 三条写入路径都遵守同一套纪律：
 * <ul>
 *   <li>{@link #postPurchaseInbound} —— 采购入库（方向 = 入，可建零余额行）；</li>
 *   <li>{@link #postSalesOutbound} —— 销售出库（方向 = 出，不建行，先判可用量）；</li>
 *   <li>{@link #postStocktakeAdjust} —— 盘点调整（方向由差异正负决定，不建行）。</li>
 * </ul>
 * 三者都：不自行开启事务、先锁余额行再算 before/after、只用**增量**方法改余额
 * （{@code incrementQuantity} / {@code decrementQuantity}），从不做「读-改-写」赋值。
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

        if (balanceDao.incrementQuantity(balance.getId(), fact.quantity(), fact.operator()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
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
        movement.setUnitCost(fact.unitCost());
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
        // 盘盈/盘亏没有成本依据，unit_cost 留空（V19 的 ck_inventory_movement_cost 允许 NULL）。
        movement.setUnitCost(null);
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
}
