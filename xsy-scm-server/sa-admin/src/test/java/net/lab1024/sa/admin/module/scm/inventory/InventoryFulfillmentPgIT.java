package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryFulfillmentService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryFulfillmentService.Command;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryFulfillmentService.Line;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryFulfillmentService.Result;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_INSUFFICIENT_AVAILABLE;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_PARAM_INVALID;
import static net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode.INVENTORY_SOURCE_ALREADY_OUTBOUND;
import static net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum.SALES_ORDER_ITEM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存域履约出库命令（P2 物流配送 L3 的库存侧）。
 *
 * <p>这一波值得钉住的，都是「命令照样返回成功、账却错了」的那一类：
 *
 * <ol>
 *   <li><b>先归还预留、再扣实物</b>：{@code ck_inventory_balance_available} 逐语句求值，
 *       顺序反了整条命令会被打回。因此「存量 10 + 本单预留 10 + 实发 5」这条组合必须一次走通
 *       （它是顺序问题的最直接暴露面，不是靠放宽 CHECK 换来通过）；</li>
 *   <li><b>少拣整条收口</b>：一条订单行终身只有一行预留（唯一索引谓词不含 status），
 *       差额只能由「预留量 − 出库流水量」现算，库里不留第二个数；</li>
 *   <li><b>跨仓预留是被释放、不是被消耗</b>：货没从那个仓走，归还方必须留下 RELEASED 事实；</li>
 *   <li><b>实发 0 不出库单</b>：整线全缺时没有实物离仓，返回的出库单 id 必须是 null；</li>
 *   <li><b>明细带订单行来源</b>：这是发车后禁止重开、以及 Finance R1 成本归属的唯一依据；</li>
 *   <li><b>一条线路一张出库单</b>：同一 routeId 重复出库必须在库里被拒（部分唯一索引）。</li>
 * </ol>
 *
 * <p>本类只调库存命令，不建配送线路：{@code routeId} 在这里是一枚不透明的来源 id，
 * 线路状态机与资格复核由 {@code DeliveryDispatchPgIT} 负责。
 */
@DisplayName("库存域履约出库命令（PG IT）")
class InventoryFulfillmentPgIT extends ScmW6PgITBase {

    @Autowired
    private InventoryFulfillmentService fulfillment;

    @Autowired
    private InventoryReservationService reservationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("存量全部被本单预留时也能出库：先归还预留、再按实发量扣实物")
    void consumesReservationInSameWarehouse() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("ff1");
        stockIn(warehouseId, skuId, "ff1", "10.0000");
        Long orderId = confirmedOrder(newCustomer(), skuId, "10.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        reserve(warehouseId, skuId, orderId, orderItemId, "10.0000");
        assertThat(reservedQuantity(warehouseId, skuId)).as("存量全被本单占住，可用量为 0").isEqualByComparingTo("10.0000");

        Result result = fulfill(9001L, warehouseId, line(orderId, orderItemId, skuId, "5.0000"));

        assertThat(result.outboundId()).as("有实物离仓就必须有出库单").isNotNull();
        assertThat(onHand(warehouseId, skuId)).as("只按实发量扣减").isEqualByComparingTo("5.0000");
        assertThat(reservedQuantity(warehouseId, skuId)).as("预留全额归还").isEqualByComparingTo("0.0000");
        assertThat(reservationStatus(orderItemId)).isEqualTo("CONSUMED");
        assertThat(reservationQuantity(orderItemId)).as("预留量不被改写，差额靠现算").isEqualByComparingTo("10.0000");
        assertThat(outboundItemCount(result.outboundId())).isEqualTo(1);
        assertThat(outboundItemOrderLine(result.outboundId())).isEqualTo(orderItemId);
        assertThat(outboundStatus(result.outboundId()))
                .as("发车出库直接是 CONFIRMED，不经过 DRAFT").isEqualTo("CONFIRMED");
        assertThat(outboundSource(result.outboundId()))
                .containsEntry("source_document_type", ScmInventorySourceDocumentTypeEnum.DELIVERY_ROUTE.name())
                .containsEntry("source_document_id", 9001L);
        assertThat(movementQuantity(result.outboundId())).isEqualByComparingTo("5.0000");
    }

    @Test
    @DisplayName("少拣：预留整条收口为 CONSUMED，差额只存在于「预留量 − 出库量」")
    void shortPickKeepsSingleReservationRow() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("ff2");
        stockIn(warehouseId, skuId, "ff2", "10.0000");
        Long orderId = confirmedOrder(newCustomer(), skuId, "5.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        reserve(warehouseId, skuId, orderId, orderItemId, "5.0000");

        Result result = fulfill(9002L, warehouseId, line(orderId, orderItemId, skuId, "3.0000"));

        assertThat(onHand(warehouseId, skuId)).as("实发 3，存量 10 → 7").isEqualByComparingTo("7.0000");
        assertThat(reservedQuantity(warehouseId, skuId)).isEqualByComparingTo("0.0000");
        assertThat(reservationRowCount(orderItemId)).as("一条订单行终身一行预留，拆不出第二行").isEqualTo(1);
        assertThat(reservationStatus(orderItemId)).isEqualTo("CONSUMED");
        assertThat(movementQuantity(result.outboundId())).as("出库流水只记实发量").isEqualByComparingTo("3.0000");
    }

    @Test
    @DisplayName("预留仓与发货仓不同：那条预留被 RELEASED，实发量全部从线路仓库扣")
    void crossWarehouseReservationIsReleasedNotConsumed() {
        Long homeWarehouse = seedWarehouseId();
        Long routeWarehouse = newWarehouse("FF3B");
        Long skuId = newOnShelfSku("ff3");
        stockIn(homeWarehouse, skuId, "ff3", "10.0000");
        stockIn(routeWarehouse, skuId, "ff3b", "10.0000");
        Long orderId = confirmedOrder(newCustomer(), skuId, "4.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        reserve(homeWarehouse, skuId, orderId, orderItemId, "4.0000");

        Result result = fulfill(9003L, routeWarehouse, line(orderId, orderItemId, skuId, "4.0000"));

        assertThat(reservedQuantity(homeWarehouse, skuId))
                .as("别仓的占用当场回落，不留悬空").isEqualByComparingTo("0.0000");
        assertThat(onHand(homeWarehouse, skuId)).as("货没从那个仓走，存量不动").isEqualByComparingTo("10.0000");
        assertThat(onHand(routeWarehouse, skuId)).as("实发量全部从线路仓库扣").isEqualByComparingTo("6.0000");
        assertThat(reservationStatus(orderItemId)).as("整条释放而不是被消耗").isEqualTo("RELEASED");
        assertThat(movementWarehouse(result.outboundId())).isEqualTo(routeWarehouse);
    }

    @Test
    @DisplayName("实发为 0 的订单行：归还预留，但不生成出库单也不生成出库行")
    void zeroQuantityLineRetiresReservationWithoutOutbound() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("ff4");
        stockIn(warehouseId, skuId, "ff4", "10.0000");
        Long orderId = confirmedOrder(newCustomer(), skuId, "5.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        reserve(warehouseId, skuId, orderId, orderItemId, "5.0000");
        int outboundBefore = outboundHeaderCount();

        Result result = fulfill(9004L, warehouseId, line(orderId, orderItemId, skuId, "0.0000"));

        assertThat(result.outboundId()).as("整条线路一行都没发货 → 不存在出库单，而不是一张空单").isNull();
        assertThat(result.shippedLineCount()).isZero();
        assertThat(outboundHeaderCount()).as("一张出库单头都不该有").isEqualTo(outboundBefore);
        assertThat(reservedQuantity(warehouseId, skuId)).isEqualByComparingTo("0.0000");
        assertThat(reservationStatus(orderItemId)).as("没出库就是被释放").isEqualTo("RELEASED");
        assertThat(onHand(warehouseId, skuId)).isEqualByComparingTo("10.0000");
    }

    /**
     * 原子性只能在<b>真提交边界</b>上验：本用例关掉外层测试事务，让命令服务自己开事务，
     * 失败时才会真的把第一行已经写下的流水一起撤掉。留在共享事务里时服务上的
     * {@code @Transactional} 只是加入外层事务，第一行的写入照样读得到 ——
     * 那样断言「一行都没扣」就成了假绿。命令服务要求调用方持有事务，因此这里显式套一层
     * {@link TransactionTemplate}，否则「缺事务」的脚手架异常会被误当成业务拒绝。
     */
    @Test
    @DisplayName("库存不足：整条线路回滚，第一行也不留在账上")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void insufficientStockRollsBackWholeRoute() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("ff5");
        stockIn(warehouseId, skuId, "ff5", "6.0000");
        Long firstOrder = confirmedOrder(newCustomer(), skuId, "4.0000");
        Long secondOrder = confirmedOrder(newCustomer(), skuId, "4.0000");
        Long firstItem = confirmedSalesOrderItemId(firstOrder);
        Long secondItem = confirmedSalesOrderItemId(secondOrder);
        int movementsBefore = movementCount(warehouseId, skuId);
        int outboundBefore = outboundHeaderCount();

        expectCode(() -> transactionTemplate.executeWithoutResult(status -> fulfill(9005L, warehouseId,
                line(firstOrder, firstItem, skuId, "4.0000"),
                line(secondOrder, secondItem, skuId, "4.0000"))), INVENTORY_INSUFFICIENT_AVAILABLE.getCode());

        assertThat(onHand(warehouseId, skuId)).as("整条回滚后存量不变").isEqualByComparingTo("6.0000");
        assertThat(movementCount(warehouseId, skuId))
                .as("第一行先写后回滚，账上不能留下它").isEqualTo(movementsBefore);
        assertThat(outboundHeaderCount()).as("出库单也必须一起消失").isEqualTo(outboundBefore);
    }

    @Test
    @DisplayName("同一条线路重复出库被部分唯一索引拒绝")
    void duplicateRouteIsRejectedByDatabase() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("ff6");
        stockIn(warehouseId, skuId, "ff6", "10.0000");
        Long orderId = confirmedOrder(newCustomer(), skuId, "3.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        Line line = line(orderId, orderItemId, skuId, "3.0000");
        fulfill(9006L, warehouseId, line);

        // 本用例到此为止：撞唯一索引会让 PostgreSQL 把当前事务置为 aborted，之后再查就是 25P02。
        expectCode(() -> fulfill(9006L, warehouseId, line), INVENTORY_SOURCE_ALREADY_OUTBOUND.getCode());
    }

    @Test
    @DisplayName("入参缺订单行、数量为负、同一订单行出现两次一律拒绝，且不落任何库存事实")
    void rejectsMalformedLines() {
        Long warehouseId = seedWarehouseId();
        Long skuId = newOnShelfSku("ff7");
        stockIn(warehouseId, skuId, "ff7", "10.0000");
        Long orderId = confirmedOrder(newCustomer(), skuId, "2.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);
        int outboundBefore = outboundHeaderCount();

        expectCode(() -> fulfill(9007L, warehouseId, line(orderId, orderItemId, skuId, "-1.0000")),
                INVENTORY_OUTBOUND_PARAM_INVALID.getCode());
        expectCode(() -> fulfillment.dispatchOutbound(new Command(9008L, warehouseId, OffsetDateTime.now(),
                "履约 IT", List.of(new Line(orderId, null, skuId, BigDecimal.ONE)))),
                INVENTORY_OUTBOUND_PARAM_INVALID.getCode());
        expectCode(() -> fulfill(9009L, warehouseId,
                line(orderId, orderItemId, skuId, "1.0000"), line(orderId, orderItemId, skuId, "1.0000")),
                INVENTORY_OUTBOUND_PARAM_INVALID.getCode());

        assertThat(onHand(warehouseId, skuId)).as("三次拒绝都没改账").isEqualByComparingTo("10.0000");
        assertThat(outboundHeaderCount()).as("也没留下出库单").isEqualTo(outboundBefore);
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private Result fulfill(Long routeId, Long warehouseId, Line... lines) {
        return fulfillment.dispatchOutbound(new Command(routeId, warehouseId, OffsetDateTime.now(), "履约 IT",
                List.of(lines)));
    }

    private Line line(Long orderId, Long orderItemId, Long skuId, String quantity) {
        return new Line(orderId, orderItemId, skuId, new BigDecimal(quantity));
    }

    private void reserve(Long warehouseId, Long skuId, Long orderId, Long orderItemId, String quantity) {
        reservationService.reserve(new ReserveInventoryFact(warehouseId, skuId, SALES_ORDER_ITEM.name(),
                orderId, orderItemId, new BigDecimal(quantity), OffsetDateTime.now(), "履约 IT"));
    }

    /** 按真实采购链路入库到<b>指定仓库</b>：{@code createDraftOrder} 写死种子仓，跨仓用例必须自己指定。 */
    private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
        Long supplierId = newPurchasableSupplier(tag, skuId);
        PurchaseOrderVO order = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
        submitOrder(order.getId());
        confirmReceipt(createReceipt(order.getId()).getId(), quantity);
        evictMybatisCache();
    }

    private Long confirmedOrder(Long customerId, Long skuId, String actualQuantity) {
        return confirmedSalesOrder(customerId, skuId, "1.0000", actualQuantity);
    }

    private BigDecimal onHand(Long warehouseId, Long skuId) {
        return inventoryBalanceDao.lockByWarehouseAndSku(warehouseId, skuId).getQuantity();
    }

    private BigDecimal reservedQuantity(Long warehouseId, Long skuId) {
        return inventoryBalanceDao.lockByWarehouseAndSku(warehouseId, skuId).getReservedQuantity();
    }

    private String reservationStatus(Long orderItemId) {
        return jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE source_document_item_id = ?",
                String.class, orderItemId);
    }

    private BigDecimal reservationQuantity(Long orderItemId) {
        return jdbc.queryForObject("SELECT quantity FROM inventory_reservation WHERE source_document_item_id = ?",
                BigDecimal.class, orderItemId);
    }

    private int reservationRowCount(Long orderItemId) {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_reservation WHERE source_document_item_id = ?",
                Integer.class, orderItemId);
    }

    private int outboundItemCount(Long outboundId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM inventory_outbound_item WHERE outbound_id = ? AND deleted = FALSE",
                Integer.class, outboundId);
    }

    private Long outboundItemOrderLine(Long outboundId) {
        return jdbc.queryForObject(
                "SELECT sales_order_item_id FROM inventory_outbound_item WHERE outbound_id = ?", Long.class, outboundId);
    }

    private String outboundStatus(Long outboundId) {
        return jdbc.queryForObject("SELECT status FROM inventory_outbound WHERE id = ?", String.class, outboundId);
    }

    private Map<String, Object> outboundSource(Long outboundId) {
        return jdbc.queryForMap("SELECT source_document_type, source_document_id FROM inventory_outbound WHERE id = ?",
                outboundId);
    }

    private int outboundHeaderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_outbound WHERE deleted = FALSE", Integer.class);
    }

    /**
     * 出库流水按 {@code SALES_OUTBOUND_ITEM} 记账，{@code source_document_id} 即出库单头 id；
     * 本类每个用例都只有一行出库明细，所以取单值即可（多行时会抛 NonUniqueResult，正是我们想看到的）。
     */
    private BigDecimal movementQuantity(Long outboundId) {
        return jdbc.queryForObject("SELECT quantity FROM inventory_movement"
                        + " WHERE source_document_type = ? AND source_document_id = ?",
                BigDecimal.class, ScmInventorySourceDocumentTypeEnum.SALES_OUTBOUND_ITEM.name(), outboundId);
    }

    private Long movementWarehouse(Long outboundId) {
        return jdbc.queryForObject("SELECT warehouse_id FROM inventory_movement"
                        + " WHERE source_document_type = ? AND source_document_id = ?",
                Long.class, ScmInventorySourceDocumentTypeEnum.SALES_OUTBOUND_ITEM.name(), outboundId);
    }
}
