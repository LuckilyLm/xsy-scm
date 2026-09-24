package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmWarehouseScopeGuard;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryReservationStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryReservationDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryReservationEntity;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_INSUFFICIENT_AVAILABLE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_RESERVATION_INVALID;

/**
 * 库存预留：占用可用量，但不改变物理库存。
 *
 * <p><b>与出库的区别</b>：出库改 {@code quantity}（物理出库），预留只改
 * {@code reserved_quantity}（占用）。两者共用同一个可用量口径
 * {@code quantity - reserved_quantity}，因此预留之后出库会立刻看到可用量变小。
 *
 * <p><b>锁序</b>：与出库一致 —— 先锁预留行（若已存在），再锁余额行；余额锁按
 * {@code (warehouse_id, sku_id)} 升序。预留行不存在时直接锁余额行。
 *
 * <p><b>事务要求</b>：{@link #reserve} 允许独立事务（{@code @Transactional}），
 * 也允许被销售订单确认复用其事务（{@code REQUIRED} 语义）。
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final InventoryReservationDao reservationDao;

    private final InventoryBalanceDao balanceDao;

    private final WarehouseService warehouseService;

    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    /**
     * 预留库存。
     *
     * <p>校验顺序：仓库存在 → 余额行存在（否则无货可占）→ 可用量足够。
     * 可用量 = {@code quantity - reserved_quantity}，与出库共用同一口径。
     *
     * @return 新建的预留 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long reserve(ReserveInventoryFact fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Reserve requires the caller's transaction");
        }
        requireFact(fact);
        warehouseService.require(fact.warehouseId());

        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(fact.warehouseId(), fact.skuId());
        if (balance == null) {
            throw new ScmBusinessException(INVENTORY_INSUFFICIENT_AVAILABLE);
        }

        BigDecimal onHand = balance.getQuantity();
        BigDecimal reserved = balance.getReservedQuantity() == null
                ? BigDecimal.ZERO : balance.getReservedQuantity();
        if (onHand.subtract(reserved).compareTo(fact.quantity()) < 0) {
            throw new ScmBusinessException(INVENTORY_INSUFFICIENT_AVAILABLE);
        }

        String operator = fact.operator() == null || fact.operator().isBlank()
                ? ScmOperator.current() : fact.operator();

        InventoryReservationEntity entity = new InventoryReservationEntity();
        entity.setWarehouseId(fact.warehouseId());
        entity.setSkuId(fact.skuId());
        entity.setSourceDocumentType(fact.sourceDocumentType());
        entity.setSourceDocumentId(fact.sourceDocumentId());
        entity.setSourceDocumentItemId(fact.sourceDocumentItemId());
        entity.setQuantity(fact.quantity());
        entity.setUnitSnapshot(balance.getUnit());
        entity.setStatus(ScmInventoryReservationStatusEnum.ACTIVE.name());
        entity.setOccurredAt(fact.occurredAt());
        entity.setOperator(operator);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);

        if (reservationDao.insertOnConflictDoNothing(entity) != 1) {
            // 同一来源行已有有效预留：视为幂等成功还是错误，由调用方语义决定。
            // 这里 fail-fast —— 静默返回会让「订单重复确认」看起来成功。
            throw new ScmBusinessException(INVENTORY_RESERVATION_INVALID);
        }

        if (balanceDao.incrementReserved(balance.getId(), fact.quantity(), operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return entity.getId();
    }

    /**
     * 释放预留：占用归还可用量。
     *
     * <p>只有 {@code ACTIVE} 可释放；重复释放会因状态条件失败（41009），
     * 避免「释放两次」把可用量虚增。
     *
     * <p>这是预留页上的显式动作，归还是<b>某个仓</b>的可用量，因此仓库必须在授权范围内。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(Long reservationId) {
        String operator = ScmOperator.current();
        InventoryReservationEntity locked = lockActiveReservation(reservationId);
        warehouseScopeGuard.require(locked.getWarehouseId());
        applyRelease(locked, operator);
    }

    /**
     * 按来源行释放（销售订单取消 / 关闭时调用）。
     *
     * <p>来源行没有有效预留时**静默返回** —— 取消一张从未预留过库存的订单是正常操作，
     * 不该报错。这与 {@link #release} 的 fail-fast 语义刻意不同：那里是「点名释放某条预留」，
     * 点不到名字说明调用方传错了 id。
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseBySource(String sourceDocumentType, Long sourceDocumentItemId) {
        if (sourceDocumentType == null || sourceDocumentItemId == null) {
            return;
        }
        InventoryReservationEntity active =
                reservationDao.selectActiveBySource(sourceDocumentType, sourceDocumentItemId);
        if (active == null) {
            return;
        }
        releaseCascade(active.getId());
    }

    /**
     * 销售订单行维度的释放（便捷入口）。
     */
    public void releaseBySalesOrderItem(Long salesOrderItemId) {
        releaseBySource(ScmInventorySourceDocumentTypeEnum.SALES_ORDER_ITEM.name(), salesOrderItemId);
    }

    /**
     * 销售订单确认时**整单预留**。
     *
     * <p>仓库由 {@link WarehouseService#defaultEnabledWarehouse()} 解析 —— 销售订单没有仓库字段
     * （G-03 单仓库口径），启用仓库不唯一时**不猜**，直接失败（41018）。
     *
     * <p><b>严格语义</b>：任一行可用量不足就抛 41011，整个确认事务回滚。
     * 也就是说「无货不能确认订单」。这是与负责人确认过的口径，不是默认行为。
     *
     * <p>锁序：按 {@code skuId} 升序逐行预留，与出库确认同一顺序。
     *
     * @param lines 订单行（只取 itemId / skuId / quantity）
     */
    public void reserveForSalesOrder(Long salesOrderId, List<OrderReserveLine> lines, OffsetDateTime occurredAt) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Long warehouseId = warehouseService.defaultEnabledWarehouse().getId();
        lines.stream()
                .sorted(Comparator.comparing(OrderReserveLine::skuId))
                .forEach(line -> reserve(new ReserveInventoryFact(
                        warehouseId,
                        line.skuId(),
                        ScmInventorySourceDocumentTypeEnum.SALES_ORDER_ITEM.name(),
                        salesOrderId,
                        line.itemId(),
                        line.quantity(),
                        occurredAt,
                        null)));
    }

    /**
     * 销售订单取消 / 关闭时**整单释放**。
     *
     * <p>按头级查全部有效预留并逐条释放；没有预留时静默返回（取消一张从未预留过的订单是正常的）。
     */
    public void releaseBySalesOrder(Long salesOrderId) {
        if (salesOrderId == null) {
            return;
        }
        List<InventoryReservationEntity> actives = reservationDao.listActiveBySourceDocument(
                ScmInventorySourceDocumentTypeEnum.SALES_ORDER_ITEM.name(), salesOrderId);
        for (InventoryReservationEntity active : actives) {
            releaseCascade(active.getId());
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 订单域级联释放：不判仓库范围。
     *
     * <p>取消订单的授权依据是订单归属（{@code sales_order.seller_id}），释放预留只是它的副作用；
     * 预留所在的那个默认启用仓未必在操作者的仓库范围内，套上仓库判据会让「取消自己的订单」
     * 变成 30005。仓管在预留页点名释放走 {@link #release}，那条必须判。
     */
    private void releaseCascade(Long reservationId) {
        applyRelease(lockActiveReservation(reservationId), ScmOperator.current());
    }

    private InventoryReservationEntity lockActiveReservation(Long reservationId) {
        InventoryReservationEntity locked = reservationDao.lockById(reservationId);
        if (locked == null || !ScmInventoryReservationStatusEnum.ACTIVE.name().equals(locked.getStatus())) {
            throw new ScmBusinessException(INVENTORY_RESERVATION_INVALID);
        }
        return locked;
    }

    private void applyRelease(InventoryReservationEntity locked, String operator) {
        InventoryBalanceEntity balance =
                balanceDao.lockByWarehouseAndSku(locked.getWarehouseId(), locked.getSkuId());
        if (balance == null) {
            // 余额行不该消失（append-only 语义下余额只增减、不删除）；这里 fail-fast 暴露数据异常。
            throw new ScmBusinessException(INVENTORY_RESERVATION_INVALID);
        }
        if (reservationDao.markReleased(locked.getId(), operator) != 1) {
            throw new ScmBusinessException(INVENTORY_RESERVATION_INVALID);
        }
        if (balanceDao.decrementReserved(balance.getId(), locked.getQuantity(), operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 订单行维度的预留入参（只带预留需要的最小字段）。
     */
    public record OrderReserveLine(Long itemId, Long skuId, BigDecimal quantity) {
    }

    private static void requireFact(ReserveInventoryFact fact) {
        if (fact == null
                || fact.warehouseId() == null
                || fact.skuId() == null
                || fact.sourceDocumentType() == null
                || fact.sourceDocumentType().isBlank()
                || fact.sourceDocumentId() == null
                || fact.sourceDocumentItemId() == null
                || fact.quantity() == null
                || fact.quantity().signum() <= 0
                || fact.occurredAt() == null) {
            throw new ScmBusinessException(INVENTORY_RESERVATION_INVALID);
        }
    }
}
