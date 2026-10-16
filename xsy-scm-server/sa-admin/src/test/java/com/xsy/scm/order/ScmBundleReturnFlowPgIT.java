package com.xsy.scm.order;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.inventory.service.InventoryFulfillmentService;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveItemForm;
import com.xsy.scm.order.domain.form.OrderReturnItemForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveItemForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.service.OrderReturnReceiptService;
import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.product.domain.form.ProductBundleReplaceForm;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.service.ProductBundleService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 PG：组合订单确认、显式预留、组成 SKU 出库及按原价成本整套退回。 */
class ScmBundleReturnFlowPgIT extends ScmW5PgITBase {

    @Autowired
    private ProductBundleService bundleService;

    @Autowired
    private InventoryFulfillmentService fulfillmentService;

    @Autowired
    private OrderReturnService returnService;

    @Autowired
    private OrderReturnReceiptService receiptService;

    @Test
    void fullBundleReturnRestocksTwoComponentsButCountsOneBundleOnce() {
        ((RequestEmployee) SmartRequestUtil.getRequestUser()).setAdministratorFlag(true);
        Long warehouse = seedWarehouseId();
        Long bundle = standardSku("RT-BUNDLE");
        Long first = standardSku("RT-A");
        Long second = standardSku("RT-B");
        var configuration = new ProductBundleReplaceForm();
        configuration.setBundleSkuId(bundle);
        configuration.setVersion(jdbc.queryForObject(
                "SELECT version FROM xsy_product_sku WHERE id=?", Integer.class, bundle));
        configuration.setComponents(List.of(component(first, "2.0000"), component(second, "3.0000")));
        bundleService.replace(configuration);

        Long firstBalance = stock(warehouse, first, "30.0000", "5.0000");
        Long secondBalance = stock(warehouse, second, "30.0000", "8.0000");
        Long customer = newCustomer();
        var orderForm = new SalesOrderAddForm();
        orderForm.setCustomerId(customer);
        orderForm.setOrderSource("ADMIN");
        orderForm.setDeliveryWarehouseId(warehouse);
        var address = new OrderAddressForm();
        address.setReceiverName("组合退货测试");
        address.setReceiverPhone("13800000000");
        address.setAddress("仓库测试地址");
        orderForm.setAddress(address);
        var item = new SalesOrderItemForm();
        item.setSkuId(bundle);
        item.setOrderedQuantity("2.0000");
        item.setManualPriceOverride(false);
        orderForm.setItems(List.of(item));
        var confirmed = salesOrderService.createAndProgress(orderForm, prefix + ":create-bundle-order");
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        Long orderId = confirmed.getOrderId();
        Long itemId = jdbc.queryForObject(
                "SELECT id FROM xsy_sales_order_item WHERE order_id=?", Long.class, orderId);
        var componentIds = jdbc.queryForList(
                "SELECT id FROM xsy_order_bundle_component WHERE order_id=? ORDER BY component_sku_id",
                Long.class, orderId);
        assertThat(componentIds).hasSize(2);
        salesOrderService.reserveStock(orderId);
        assertThat(reserved(firstBalance)).isEqualByComparingTo("4.0000");
        assertThat(reserved(secondBalance)).isEqualByComparingTo("6.0000");

        var outbound = fulfillmentService.dispatchOutbound(new InventoryFulfillmentService.Command(
                998991L, warehouse, OffsetDateTime.now(), "bundle-return-it", List.of(
                        new InventoryFulfillmentService.Line(orderId, itemId, first, new BigDecimal("4.0000"),
                                "kg", BigDecimal.ONE, componentIds.get(0), bundle, new BigDecimal("2.0000")),
                        new InventoryFulfillmentService.Line(orderId, itemId, second, new BigDecimal("6.0000"),
                                "kg", BigDecimal.ONE, componentIds.get(1), bundle, new BigDecimal("2.0000")))));
        assertThat(outbound.shippedLineCount()).isEqualTo(1);
        assertThat(quantity(firstBalance)).isEqualByComparingTo("26.0000");
        assertThat(quantity(secondBalance)).isEqualByComparingTo("24.0000");

        var requested = new OrderReturnItemForm();
        requested.setOrderItemId(itemId);
        requested.setRequestedQuantity("1.0000");
        var createReturn = new OrderReturnAddForm();
        createReturn.setOrderId(orderId);
        createReturn.setReason("整套组合商品退货测试");
        createReturn.setItems(List.of(requested));
        var pending = returnService.create(createReturn, prefix + ":request-return");
        var approvedItem = new OrderReturnApproveItemForm();
        approvedItem.setOrderItemId(itemId);
        approvedItem.setApprovedQuantity("1.0000");
        var approve = new OrderReturnApproveForm();
        approve.setReturnId(pending.getReturnId());
        approve.setVersion(pending.getVersion());
        approve.setItems(List.of(approvedItem));
        var approved = returnService.approve(approve, prefix + ":approve-return");

        var receivedItem = new OrderReturnReceiveItemForm();
        receivedItem.setReturnItemId(approved.getItems().getFirst().getReturnItemId());
        receivedItem.setQuantity("1.0000");
        receivedItem.setDisposition("RETURN_TO_STOCK");
        var receive = new OrderReturnReceiveForm();
        receive.setReturnId(approved.getReturnId());
        receive.setVersion(approved.getVersion());
        receive.setWarehouseId(warehouse);
        receive.setItems(List.of(receivedItem));
        var receipt = receiptService.receive(receive, prefix + ":receive-return");

        assertThat(receipt.getItems()).hasSize(2);
        assertThat(receipt.getItems()).extracting(row -> row.getStockQuantity().toPlainString())
                .containsExactlyInAnyOrder("2.0000", "3.0000");
        assertThat(receipt.getItems().stream().filter(row ->
                Boolean.TRUE.equals(row.getBundleReceiptCounted())).count()).isEqualTo(1);
        assertThat(returnService.detail(approved.getReturnId()).getItems().getFirst().getReceivedQuantity())
                .isEqualByComparingTo("1.0000");
        assertThat(quantity(firstBalance)).isEqualByComparingTo("28.0000");
        assertThat(quantity(secondBalance)).isEqualByComparingTo("27.0000");
        assertThat(reserved(firstBalance)).isEqualByComparingTo("0.0000");
        assertThat(reserved(secondBalance)).isEqualByComparingTo("0.0000");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_inventory_movement WHERE movement_type='SALES_RETURN_IN' "
                + "AND sku_id=?", Integer.class, bundle)).isZero();
    }

    private Long standardSku(String suffix) {
        var spu = new ProductSpuAddForm();
        spu.setSpuCode(prefix + suffix);
        spu.setName(prefix + suffix + "商品");
        spu.setStatus("ON_SHELF");
        spu.setCategoryId(jdbc.queryForObject(
                "SELECT id FROM xsy_product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE",
                Long.class));
        var sku = new ProductSkuForm();
        sku.setSkuCode(prefix + suffix + "-K");
        sku.setSpecName("规格" + suffix);
        sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
        sku.setSaleUnit("kg");
        sku.setProductType("STANDARD");
        sku.setMarketPrice(new BigDecimal("10.0000"));
        sku.setStatus("ON_SHELF");
        sku.setDefaultFlag(true);
        spu.setSkuList(new ArrayList<>(List.of(sku)));
        Long spuId = productSpuService.add(spu);
        return jdbc.queryForObject("SELECT id FROM xsy_product_sku WHERE spu_id=? AND deleted=FALSE",
                Long.class, spuId);
    }

    private static ProductBundleReplaceForm.Component component(Long sku, String quantity) {
        var item = new ProductBundleReplaceForm.Component();
        item.setSkuId(sku);
        item.setQuantityPerBundleSaleUnit(quantity);
        return item;
    }

    private Long stock(Long warehouse, Long sku, String quantity, String cost) {
        return jdbc.queryForObject(
                "INSERT INTO xsy_inventory_balance (warehouse_id,sku_id,unit,quantity,reserved_quantity,avg_cost)"
                + " VALUES (?,?,'kg',?::numeric,0.0000,?::numeric) RETURNING id",
                Long.class, warehouse, sku, quantity, cost);
    }

    private BigDecimal quantity(Long id) {
        return jdbc.queryForObject("SELECT quantity FROM xsy_inventory_balance WHERE id=?",
                BigDecimal.class, id);
    }

    private BigDecimal reserved(Long id) {
        return jdbc.queryForObject("SELECT reserved_quantity FROM xsy_inventory_balance WHERE id=?",
                BigDecimal.class, id);
    }
}
