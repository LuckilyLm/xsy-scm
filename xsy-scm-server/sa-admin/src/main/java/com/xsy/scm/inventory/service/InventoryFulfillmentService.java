package com.xsy.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.inventory.constant.InventoryErrorCode;
import com.xsy.scm.inventory.constant.ScmInventoryOutboundStatusEnum;
import com.xsy.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import com.xsy.scm.inventory.dao.InventoryBalanceDao;
import com.xsy.scm.inventory.dao.InventoryOutboundDao;
import com.xsy.scm.inventory.dao.InventoryOutboundItemDao;
import com.xsy.scm.inventory.dao.InventoryReservationDao;
import com.xsy.scm.inventory.domain.InventoryOutboundFact;
import com.xsy.scm.inventory.domain.InventoryPromotionGiftFact;
import com.xsy.scm.inventory.domain.entity.InventoryBalanceEntity;
import com.xsy.scm.inventory.domain.entity.InventoryOutboundEntity;
import com.xsy.scm.inventory.domain.entity.InventoryOutboundItemEntity;
import com.xsy.scm.inventory.domain.entity.InventoryReservationEntity;
import com.xsy.scm.warehouse.service.WarehouseService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_INSUFFICIENT_AVAILABLE;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_PARAM_INVALID;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_RESERVATION_INVALID;

/**
 * 配送发车时确认出库，没有出库草稿。必须先释放预留量，再按实发量扣减库存，否则可能触发预留量不能大于库存量的约束。先锁配送单据，再按仓库和商品规格顺序锁余额。少拣时释放整笔预留，只扣实际发货仓的库存。
 */
@Service
@RequiredArgsConstructor
public class InventoryFulfillmentService {

    private final InventoryOutboundDao inventoryOutboundDao;

    private final InventoryOutboundItemDao inventoryOutboundItemDao;

    private final InventoryReservationDao inventoryReservationDao;

    private final InventoryBalanceDao inventoryBalanceDao;

    private final InventoryOutboundNumberGenerator numberGenerator;

    private final InventoryCommandService inventoryCommandService;

    private final WarehouseService warehouseService;

    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    /**
     * 一条出库明细：一个销售订单行，量取分拣实发量。
     *
     * @param quantity
     *            实发量，{@code >= 0}；为 0 表示该行全缺（OUT_OF_STOCK），不生成出库行
     */
    public record Line(Long salesOrderId, Long salesOrderItemId, Long skuId, BigDecimal quantity, String stockUnit,
        BigDecimal factor, Long bundleComponentId, Long bundleSkuId, BigDecimal bundleQuantity) {
        public Line(Long salesOrderId, Long salesOrderItemId, Long skuId, BigDecimal quantity, String stockUnit,
            BigDecimal factor) {
            this(salesOrderId, salesOrderItemId, skuId, quantity, stockUnit, factor, null, null, null);
        }

        public Line(Long salesOrderId, Long salesOrderItemId, Long skuId, BigDecimal quantity) {
            this(salesOrderId, salesOrderItemId, skuId, quantity, null, null);
        }

        public String reservationType() {
            return bundleComponentId == null
                ? ScmInventorySourceDocumentTypeEnum.SALES_ORDER_ITEM.name()
                : ScmInventorySourceDocumentTypeEnum.ORDER_BUNDLE_COMPONENT.name();
        }

        public Long reservationItemId() {
            return bundleComponentId == null ? salesOrderItemId : bundleComponentId;
        }

        public BigDecimal stockQuantity() {
            return InventorySaleUnitConverter.toStockQuantity(quantity, stockUnit, factor);
        }
    }

    /**
     * @param routeId
     *            来源配送线路 id，记入出库单头的 source_document_id 并参与防重唯一索引
     * @param warehouseId
     *            发货仓，即本次 SALES_OUT 的仓库
     * @param occurredAt
     *            出库发生时刻 —— 由调用方给出真正的发车时刻，不在这里取 now()
     * @param operator
     *            发车操作人，同时作为流水与单据的操作者
     */
    public record Command(Long routeId, Long warehouseId, java.time.OffsetDateTime occurredAt, String operator,
        List<Line> lines) {
    }

    /**
     * @param outboundId
     *            出库单 id；整条线路一行都没发货时为 null（不是一张空单）
     * @param outboundNo
     *            出库单号，随 outboundId 同生同灭
     */
    public record Result(Long outboundId, String outboundNo, int shippedLineCount) {
    }

    /**
     * 一条赠品出库明细：一个已确定的赠品权益行。
     *
     * @param giftId
     *            {@code xsy_order_promotion_gift.id}，同时是防重锚点
     * @param quantity
     *            赠品数量，恒 &gt; 0
     */
    public record GiftLine(Long giftId, Long salesOrderId, Long skuId, BigDecimal quantity) {
    }

    /**
     * @param routeId
     *            发车业务数据（配送线路 id），只做流水头级溯源
     */
    public record GiftCommand(Long routeId, Long warehouseId, java.time.OffsetDateTime occurredAt, String operator,
        List<GiftLine> lines) {
    }

    /**
     * 发车正式出库：归还预留 → 生成已确认出库单 → 逐行写 SALES_OUT 并扣余额。
     *
     * <p>
     * 必须在调用方事务内调用（与 {@link InventoryCommandService} 同一要求），任一环节失败整条线路一起回滚 —— 不允许「出一半」。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result dispatchOutbound(Command command) {
        requireCommand(command);
        warehouseService.require(command.warehouseId());
        // 授权判定取本次真正要扣减的仓库，不信任调用方传来的任何其它仓库
        warehouseScopeGuard.require(command.warehouseId());

        Map<String, InventoryReservationEntity> reservations = lockReservations(command);
        Map<String, InventoryBalanceEntity> balances = lockBalances(command, reservations);
        retireReservations(command, reservations, balances);

        List<Line> shipped = command
            .lines().stream().filter(line -> line.quantity().compareTo(BigDecimal.ZERO) > 0).sorted(Comparator
                .comparing(Line::skuId).thenComparing(Line::salesOrderItemId).thenComparing(Line::reservationItemId))
            .toList();
        // 全部订单行都缺：没有实物离开仓库，因此不生成空出库单，outbound 相关字段留空。
        if (shipped.isEmpty()) {
            return new Result(null, null, 0);
        }

        InventoryOutboundEntity outbound = insertOutbound(command);
        for (Line line : shipped) {
            InventoryOutboundItemEntity item = insertItem(outbound.getId(), line, command.operator());
            String unit = inventoryCommandService
                .postSalesOutbound(new InventoryOutboundFact(command.warehouseId(), line.skuId(), outbound.getId(),
                    item.getId(), line.stockQuantity(), null, command.occurredAt(), command.operator()));
            inventoryOutboundItemDao.updateUnitSnapshot(item.getId(), unit, command.operator());
        }
        return new Result(outbound.getId(), outbound.getOutboundNo(),
            (int) shipped.stream().map(Line::salesOrderItemId).distinct().count());
    }

    /**
     * 促销赠品发车出库。
     *
     * <p>
     * 与 {@link #dispatchOutbound} 同一事务要求（任一环节失败整条线路一起回滚），但不碰预留：赠品从不预留，只在发车这一刻从可用量里出。全部赠品按
     * {@code (warehouse_id, sku_id)} 升序逐条写流水，与销售出库同一加锁顺序，避免与其它库存命令形成死锁。
     *
     * <p>
     * 缺货由调用方先用 {@link #requirePromotionGiftStock} 预检；这里逐条再校验一次是额外保护 —— 预检与出库之间若有并发，仍以出库时的可用量为准。
     */
    @Transactional(rollbackFor = Exception.class)
    public int dispatchPromotionGiftOutbound(GiftCommand command) {
        requireGiftCommand(command);
        warehouseService.require(command.warehouseId());
        warehouseScopeGuard.require(command.warehouseId());

        List<GiftLine> shipped = command.lines().stream().filter(line -> line.quantity().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing(GiftLine::skuId).thenComparing(GiftLine::giftId)).toList();
        for (GiftLine line : shipped) {
            inventoryCommandService
                .postPromotionGiftOutbound(new InventoryPromotionGiftFact(command.warehouseId(), line.skuId(),
                    command.routeId(), line.giftId(), line.quantity(), command.occurredAt(), command.operator()));
        }
        return shipped.size();
    }

    /**
     * 赠品库存预检：只锁余额并校验可用量，不写任何流水。
     *
     * <p>
     * 发车时先跑一遍，让「赠品缺货」在动正常商品之前就暴露出来：整笔发车本来就是一个事务、失败都会回滚，但先失败能省掉一次完整出库的代价，也让报错直接指向真正的缺口。
     *
     * <p>
     * 同一 SKU 可能被多张订单的赠品同时要，因此按 SKU 汇总后再比可用量 —— 逐条都比得过、合起来不够，是赠品场景下最容易漏的一种缺货。
     */
    @Transactional(rollbackFor = Exception.class)
    public void requirePromotionGiftStock(Long warehouseId, List<GiftLine> lines) {
        if (warehouseId == null || lines == null || lines.isEmpty()) {
            return;
        }
        warehouseService.require(warehouseId);
        warehouseScopeGuard.require(warehouseId);

        Map<Long, BigDecimal> wanted = new LinkedHashMap<>();
        for (GiftLine line : lines) {
            if (line.quantity() != null && line.quantity().compareTo(BigDecimal.ZERO) > 0) {
                wanted.merge(line.skuId(), line.quantity(), BigDecimal::add);
            }
        }
        if (wanted.isEmpty()) {
            return;
        }

        Map<Long, InventoryBalanceEntity> balances = new LinkedHashMap<>();
        for (Long skuId : wanted.keySet().stream().sorted().toList()) {
            InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(warehouseId, skuId);
            if (balance == null) {
                // 没有余额行 = 从未入库 = 无货可出
                throw new ScmBusinessException(INVENTORY_INSUFFICIENT_AVAILABLE);
            }
            balances.put(skuId, balance);
        }
        for (Map.Entry<Long, BigDecimal> entry : wanted.entrySet()) {
            InventoryBalanceEntity balance = balances.get(entry.getKey());
            BigDecimal reserved = balance.getReservedQuantity() == null
                ? BigDecimal.ZERO
                : balance.getReservedQuantity();
            if (balance.getQuantity().subtract(reserved).compareTo(entry.getValue()) < 0) {
                throw new ScmBusinessException(INVENTORY_INSUFFICIENT_AVAILABLE);
            }
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 域边界校验：线路侧构造的明细一旦缺列或重复引用同一订单行，就会造成静默重复扣库存。
     */
    private static void requireCommand(Command command) {
        if (command == null || command.routeId() == null || command.warehouseId() == null
            || command.occurredAt() == null || command.operator() == null || command.operator().isBlank()
            || command.lines() == null || command.lines().isEmpty()) {
            throw new ScmBusinessException(INVENTORY_OUTBOUND_PARAM_INVALID);
        }
        Set<String> seenSources = new HashSet<>();
        for (Line line : command.lines()) {
            if (line.salesOrderId() == null || line.salesOrderItemId() == null || line.skuId() == null
                || line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) < 0
                || (line.bundleComponentId() != null && (line.bundleSkuId() == null || line.bundleQuantity() == null
                    || line.bundleQuantity().signum() < 0))
                || !seenSources.add(line.reservationType() + ":" + line.reservationItemId())) {
                throw new ScmBusinessException(INVENTORY_OUTBOUND_PARAM_INVALID);
            }
        }
    }

    /**
     * 赠品命令的域边界校验：重复引用同一权益行会造成静默重复扣库存，因此按 giftId 去重。
     */
    private static void requireGiftCommand(GiftCommand command) {
        if (command == null || command.routeId() == null || command.warehouseId() == null
            || command.occurredAt() == null || command.operator() == null || command.operator().isBlank()
            || command.lines() == null || command.lines().isEmpty()) {
            throw new ScmBusinessException(INVENTORY_OUTBOUND_PARAM_INVALID);
        }
        Set<Long> seenGifts = new HashSet<>();
        for (GiftLine line : command.lines()) {
            if (line.giftId() == null || line.skuId() == null || line.quantity() == null
                || line.quantity().compareTo(BigDecimal.ZERO) < 0 || !seenGifts.add(line.giftId())) {
                throw new ScmBusinessException(INVENTORY_OUTBOUND_PARAM_INVALID);
            }
        }
    }

    /**
     * 按 (warehouse_id, sku_id, id) 升序逐行加锁，返回「订单行 → 有效预留」。
     */
    private Map<String, InventoryReservationEntity> lockReservations(Command command) {
        List<Long> orderLineIds = command.lines().stream().filter(line -> line.bundleComponentId() == null)
            .map(Line::salesOrderItemId).toList();
        List<Long> componentIds = command.lines().stream().map(Line::bundleComponentId)
            .filter(java.util.Objects::nonNull).toList();
        List<InventoryReservationEntity> found = new java.util.ArrayList<>();
        if (!orderLineIds.isEmpty()) {
            found.addAll(inventoryReservationDao
                .listActiveBySourceItemIds(ScmInventorySourceDocumentTypeEnum.SALES_ORDER_ITEM.name(), orderLineIds));
        }
        if (!componentIds.isEmpty()) {
            found.addAll(inventoryReservationDao.listActiveBySourceItemIds(
                ScmInventorySourceDocumentTypeEnum.ORDER_BUNDLE_COMPONENT.name(), componentIds));
        }
        found.sort(Comparator.comparing(InventoryReservationEntity::getWarehouseId)
            .thenComparing(InventoryReservationEntity::getSkuId).thenComparing(InventoryReservationEntity::getId));
        Map<String, InventoryReservationEntity> locked = new LinkedHashMap<>();
        for (InventoryReservationEntity candidate : found) {
            InventoryReservationEntity row = inventoryReservationDao.lockById(candidate.getId());
            if (row != null) {
                locked.put(row.getSourceDocumentType() + ":" + row.getSourceDocumentItemId(), row);
            }
        }
        return locked;
    }

    /**
     * 预锁本事务要用到的全部余额行：发货仓的每个出库 SKU，加上每条预留自己所在仓的 SKU。跨仓释放与本地扣减因此使用同一个升序加锁顺序。
     */
    private Map<String, InventoryBalanceEntity> lockBalances(Command command,
        Map<String, InventoryReservationEntity> reservations) {
        Map<String, long[]> wanted = new HashMap<>();
        for (Line line : command.lines()) {
            wanted.putIfAbsent(balanceKey(command.warehouseId(), line.skuId()),
                new long[]{command.warehouseId(), line.skuId()});
        }
        for (InventoryReservationEntity reservation : reservations.values()) {
            wanted.putIfAbsent(balanceKey(reservation.getWarehouseId(), reservation.getSkuId()),
                new long[]{reservation.getWarehouseId(), reservation.getSkuId()});
        }
        List<long[]> ordered = wanted.values().stream()
            .sorted(Comparator.comparingLong((long[] pair) -> pair[0]).thenComparingLong(pair -> pair[1])).toList();
        Map<String, InventoryBalanceEntity> locked = new HashMap<>();
        for (long[] pair : ordered) {
            InventoryBalanceEntity balance = inventoryBalanceDao.lockByWarehouseAndSku(pair[0], pair[1]);
            if (balance != null) {
                locked.put(balanceKey(pair[0], pair[1]), balance);
            }
        }
        for (Line line : command.lines()) {
            if (line.quantity().signum() > 0 && line.stockUnit() != null) {
                InventoryBalanceEntity balance = locked.get(balanceKey(command.warehouseId(), line.skuId()));
                if (balance != null && !line.stockUnit().equals(balance.getUnit())) {
                    throw new ScmBusinessException(InventoryErrorCode.INVENTORY_UNIT_MISMATCH);
                }
            }
        }
        return locked;
    }

    /**
     * 整条归还预留：先动 {@code reserved_quantity}（必须早于任何实物扣减，见类注释），再更新状态。
     */
    private void retireReservations(Command command, Map<String, InventoryReservationEntity> reservations,
        Map<String, InventoryBalanceEntity> balances) {
        Set<String> shippedHere = command.lines().stream()
            .filter(line -> line.quantity().compareTo(BigDecimal.ZERO) > 0)
            .map(line -> line.reservationType() + ":" + line.reservationItemId())
            .collect(java.util.stream.Collectors.toSet());
        for (InventoryReservationEntity reservation : reservations.values()) {
            InventoryBalanceEntity balance = balances
                .get(balanceKey(reservation.getWarehouseId(), reservation.getSkuId()));
            if (balance == null) {
                throw new ScmBusinessException(InventoryErrorCode.INVENTORY_BALANCE_NOT_FOUND);
            }
            if (inventoryBalanceDao.decrementReserved(balance.getId(), reservation.getQuantity(),
                command.operator()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
            boolean consumed = command.warehouseId().equals(reservation.getWarehouseId()) && shippedHere
                .contains(reservation.getSourceDocumentType() + ":" + reservation.getSourceDocumentItemId());
            int affected = consumed
                ? inventoryReservationDao.markConsumed(reservation.getId(), command.operator())
                : inventoryReservationDao.markReleased(reservation.getId(), command.operator());
            if (affected != 1) {
                throw new ScmBusinessException(INVENTORY_RESERVATION_INVALID);
            }
        }
    }

    private InventoryOutboundEntity insertOutbound(Command command) {
        InventoryOutboundEntity entity = new InventoryOutboundEntity();
        entity.setOutboundNo(numberGenerator.next());
        entity.setWarehouseId(command.warehouseId());
        entity.setStatus(ScmInventoryOutboundStatusEnum.CONFIRMED.name());
        // 发车时刻即确认时刻：流水的 occurred_at / operator 都从这里取，不用 now() 顶替。
        entity.setConfirmedAt(command.occurredAt());
        entity.setOperator(command.operator());
        entity.setSourceDocumentType(ScmInventorySourceDocumentTypeEnum.DELIVERY_ROUTE.name());
        entity.setSourceDocumentId(command.routeId());
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy(command.operator());
        entity.setUpdatedBy(command.operator());
        try {
            inventoryOutboundDao.insert(entity);
        } catch (DuplicateKeyException e) {
            // 线路行锁已经串行化了正常路径；撞到这里说明有并发或其他途径在重复生成同一张单。
            throw new ScmBusinessException(InventoryErrorCode.INVENTORY_SOURCE_ALREADY_OUTBOUND);
        }
        return entity;
    }

    private InventoryOutboundItemEntity insertItem(Long outboundId, Line line, String operator) {
        InventoryOutboundItemEntity item = new InventoryOutboundItemEntity();
        item.setOutboundId(outboundId);
        item.setSkuId(line.skuId());
        item.setQuantity(line.quantity());
        if (line.stockUnit() != null)
            item.setStockQuantity(line.stockQuantity());
        item.setSalesOrderId(line.salesOrderId());
        item.setSalesOrderItemId(line.salesOrderItemId());
        item.setBundleComponentId(line.bundleComponentId());
        item.setBundleSaleQuantity(line.bundleQuantity());
        item.setVersion(0);
        item.setDeleted(false);
        item.setCreatedBy(operator);
        item.setUpdatedBy(operator);
        inventoryOutboundItemDao.insert(item);
        return item;
    }

    private static String balanceKey(Long warehouseId, Long skuId) {
        return warehouseId + ":" + skuId;
    }
}
