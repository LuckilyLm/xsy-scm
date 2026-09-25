package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmWarehouseScopeGuard;
import net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryOutboundStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryOutboundDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryOutboundItemDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryReservationDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryOutboundFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryOutboundEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryOutboundItemEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryReservationEntity;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
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

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_RESERVATION_INVALID;

/**
 * 订单履约出库命令 —— 配送 L3 发车时库存域唯一的写入口（P2 裁决第 3、4、5、6、7、8、9 条）。
 *
 * <p>与手工出库单（{@link InventoryOutboundService}）的分工：那条链路是「仓库自己开草稿再确认」，
 * 本命令是「外部单据一次性产生已确认出库单」。出库单**直接以 {@code CONFIRMED} 落库、不经过
 * {@code DRAFT}** —— 手工页的编辑 / 重复确认 / 取消都只认 DRAFT，因此代表真实发货的这张单
 * 天然碰不到，不需要在出库单上再加一层「是否被线路占用」的状态。
 *
 * <p><b>步骤顺序是库里的 CHECK 逼出来的，不是风格</b>：
 * {@code ck_inventory_balance_available (reserved_quantity <= quantity)} 逐语句求值，
 * 所以必须<b>先归还预留、再扣减实物</b>。反例：存量 10、本单预留 10、实发 5 ——
 * 先扣实物会写出 {@code quantity = 5 / reserved_quantity = 10}，整条发车事务被打回。
 * 这也解释了为什么不能直接复用 {@code InventoryOutboundService.confirm}：那条链路要求调用方
 * 自己先把预留释放干净，顺序错了就是自己预留了自己出不了库。
 *
 * <p><b>锁序</b>：沿用 §8.1「单据锁先于余额锁，余额锁按 {@code (warehouse_id, sku_id)} 升序」。
 * 本命令的「单据锁」是调用方持有的配送线路行锁，因此这里先按既定顺序锁预留，
 * 再把<strong>预留所在仓</strong>与<strong>发货仓</strong>两侧涉及的余额行一次性按升序预锁；
 * 之后 {@code decrementReserved} 与 {@link InventoryCommandService#postSalesOutbound} 只是
 * 重复获取本事务已持有的锁。并发两条线路在同一 SKU 上交错时不会互为逆序。
 *
 * <p><b>少拣与跨仓</b>：预留按整条生命周期收口（裁决第 6 条）—— 同一事务里整条归还
 * {@code reserved_quantity}，实发量只从发货仓的可用量扣；差额不另存字段，
 * 由「{@code inventory_reservation.quantity} − 对应 {@code SALES_OUT.quantity}」现算。
 * 预留仓与发货仓一致且本行确有出库时置 {@code CONSUMED}，否则置 {@code RELEASED}
 * （货根本没从那个仓走，那条预留是被释放掉的）。唯一索引
 * {@code uk_inventory_reservation_source_active} 的谓词不含 {@code status}，
 * 一条订单行终身只有一行预留，因此不存在拆行的选项。
 */
@Service
@RequiredArgsConstructor
public class InventoryFulfillmentService {

    private final InventoryOutboundDao outboundDao;

    private final InventoryOutboundItemDao itemDao;

    private final InventoryReservationDao reservationDao;

    private final InventoryBalanceDao balanceDao;

    private final InventoryOutboundNumberGenerator numberGenerator;

    private final InventoryCommandService inventoryCommandService;

    private final WarehouseService warehouseService;

    private final ScmWarehouseScopeGuard warehouseScopeGuard;

    /**
     * 一条出库明细：一个销售订单行，量取分拣实发量。
     *
     * @param quantity 实发量，{@code >= 0}；为 0 表示该行全缺（OUT_OF_STOCK），不生成出库行
     */
    public record Line(Long salesOrderId, Long salesOrderItemId, Long skuId, BigDecimal quantity) {
    }

    /**
     * @param routeId     来源配送线路 id，落进入库单头的 source_document_id 并参与防重唯一索引
     * @param warehouseId 发货仓，即本次 SALES_OUT 的仓库
     * @param occurredAt  出库发生时刻 —— 由调用方给出**发车时刻**，不在这里取 now()
     * @param operator    发车操作人，同时作为流水与单据的操作者
     */
    public record Command(Long routeId, Long warehouseId, java.time.OffsetDateTime occurredAt, String operator,
                          List<Line> lines) {
    }

    /**
     * @param outboundId 出库单 id；整条线路一行都没发货时为 null（不是一张空单）
     * @param outboundNo 出库单号，随 outboundId 同生同灭
     */
    public record Result(Long outboundId, String outboundNo, int shippedLineCount) {
    }

    /**
     * 发车正式出库：归还预留 → 生成已确认出库单 → 逐行写 SALES_OUT 并扣余额。
     *
     * <p>必须在调用方事务内调用（与 {@link InventoryCommandService} 同一纪律），
     * 任一环节失败整条线路一起回滚 —— 不允许「出一半」。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result dispatchOutbound(Command command) {
        requireCommand(command);
        warehouseService.require(command.warehouseId());
        // 授权判定取本次真正要扣减的仓库，不信任调用方传来的任何其它仓库
        warehouseScopeGuard.require(command.warehouseId());

        Map<Long, InventoryReservationEntity> reservations = lockReservations(command);
        Map<String, InventoryBalanceEntity> balances = lockBalances(command, reservations);
        retireReservations(command, reservations, balances);

        List<Line> shipped = command.lines().stream()
                .filter(line -> line.quantity().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(Line::skuId).thenComparing(Line::salesOrderItemId))
                .toList();
        // 全部订单行都缺：没有实物离开仓库，因此**不是一张空出库单**，outbound 相关字段留空。
        if (shipped.isEmpty()) {
            return new Result(null, null, 0);
        }

        InventoryOutboundEntity outbound = insertOutbound(command);
        for (Line line : shipped) {
            InventoryOutboundItemEntity item = insertItem(outbound.getId(), line, command.operator());
            String unit = inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
                    command.warehouseId(), line.skuId(), outbound.getId(), item.getId(),
                    line.quantity(), null, command.occurredAt(), command.operator()));
            itemDao.updateUnitSnapshot(item.getId(), unit, command.operator());
        }
        return new Result(outbound.getId(), outbound.getOutboundNo(), shipped.size());
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
        Set<Long> seenOrderLines = new HashSet<>();
        for (Line line : command.lines()) {
            if (line.salesOrderId() == null || line.salesOrderItemId() == null || line.skuId() == null
                    || line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) < 0
                    || !seenOrderLines.add(line.salesOrderItemId())) {
                throw new ScmBusinessException(INVENTORY_OUTBOUND_PARAM_INVALID);
            }
        }
    }

    /**
     * 按 (warehouse_id, sku_id, id) 升序逐行加锁，返回「订单行 → 有效预留」。
     */
    private Map<Long, InventoryReservationEntity> lockReservations(Command command) {
        List<Long> orderLineIds = command.lines().stream().map(Line::salesOrderItemId).toList();
        List<InventoryReservationEntity> found = reservationDao.listActiveBySourceItemIds(
                ScmInventorySourceDocumentTypeEnum.SALES_ORDER_ITEM.name(), orderLineIds);
        Map<Long, InventoryReservationEntity> locked = new LinkedHashMap<>();
        for (InventoryReservationEntity candidate : found) {
            InventoryReservationEntity row = reservationDao.lockById(candidate.getId());
            if (row != null) {
                locked.put(row.getSourceDocumentItemId(), row);
            }
        }
        return locked;
    }

    /**
     * 预锁本事务要用到的全部余额行：发货仓的每个出库 SKU，加上每条预留自己所在仓的 SKU。
     * 跨仓释放与本地扣减因此落在同一把升序锁序里。
     */
    private Map<String, InventoryBalanceEntity> lockBalances(Command command,
                                                             Map<Long, InventoryReservationEntity> reservations) {
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
                .sorted(Comparator.comparingLong((long[] pair) -> pair[0]).thenComparingLong(pair -> pair[1]))
                .toList();
        Map<String, InventoryBalanceEntity> locked = new HashMap<>();
        for (long[] pair : ordered) {
            InventoryBalanceEntity balance = balanceDao.lockByWarehouseAndSku(pair[0], pair[1]);
            if (balance != null) {
                locked.put(balanceKey(pair[0], pair[1]), balance);
            }
        }
        return locked;
    }

    /**
     * 整条归还预留：先动 {@code reserved_quantity}（必须早于任何实物扣减，见类注释），再收口状态。
     */
    private void retireReservations(Command command, Map<Long, InventoryReservationEntity> reservations,
                                    Map<String, InventoryBalanceEntity> balances) {
        Set<Long> shippedHere = command.lines().stream()
                .filter(line -> line.quantity().compareTo(BigDecimal.ZERO) > 0)
                .map(Line::salesOrderItemId)
                .collect(java.util.stream.Collectors.toSet());
        for (InventoryReservationEntity reservation : reservations.values()) {
            InventoryBalanceEntity balance = balances.get(
                    balanceKey(reservation.getWarehouseId(), reservation.getSkuId()));
            if (balance == null) {
                throw new ScmBusinessException(InventoryErrorCode.INVENTORY_BALANCE_NOT_FOUND);
            }
            if (balanceDao.decrementReserved(balance.getId(), reservation.getQuantity(), command.operator()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
            boolean consumed = command.warehouseId().equals(reservation.getWarehouseId())
                    && shippedHere.contains(reservation.getSourceDocumentItemId());
            int affected = consumed
                    ? reservationDao.markConsumed(reservation.getId(), command.operator())
                    : reservationDao.markReleased(reservation.getId(), command.operator());
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
            outboundDao.insert(entity);
        } catch (DuplicateKeyException e) {
            // 线路行锁已经串行化了正常路径；撞到这里说明有并发或旁路在重复生成同一张单。
            throw new ScmBusinessException(InventoryErrorCode.INVENTORY_SOURCE_ALREADY_OUTBOUND);
        }
        return entity;
    }

    private InventoryOutboundItemEntity insertItem(Long outboundId, Line line, String operator) {
        InventoryOutboundItemEntity item = new InventoryOutboundItemEntity();
        item.setOutboundId(outboundId);
        item.setSkuId(line.skuId());
        item.setQuantity(line.quantity());
        item.setSalesOrderId(line.salesOrderId());
        item.setSalesOrderItemId(line.salesOrderItemId());
        item.setVersion(0);
        item.setDeleted(false);
        item.setCreatedBy(operator);
        item.setUpdatedBy(operator);
        itemDao.insert(item);
        return item;
    }

    private static String balanceKey(Long warehouseId, Long skuId) {
        return warehouseId + ":" + skuId;
    }
}
