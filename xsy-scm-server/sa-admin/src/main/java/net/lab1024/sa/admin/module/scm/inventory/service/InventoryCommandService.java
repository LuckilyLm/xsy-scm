package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryMovementDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryOutboundFact;
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
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_INSUFFICIENT_AVAILABLE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_UNIT_MISMATCH;

/**
 * 将已确认的采购收货行记入库存，必须参与调用方事务，失败时随收货确认一起回滚。
 *
 * <p>调用方须先持有采购/收货锁，再按 {@code (warehouseId, skuId)} 升序写入库存，
 * 避免多行收货以相反顺序获取余额锁。
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
}
