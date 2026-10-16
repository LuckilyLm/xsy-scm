package com.xsy.scm.order;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.product.domain.form.ProductBundleReplaceForm;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.service.ProductBundleService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** 后台明确选仓的真实订单录入 → 自动确认 → 组成价格快照，不按系统默认仓替代。 */
class ScmBundleAdminOrderPgIT extends ScmW5PgITBase {

    @Autowired
    private ProductBundleService bundleService;

    @Test
    void adminBundleOrderFreezesExplicitWarehouseAndComponentAllocations() {
        Long warehouse = seedWarehouseId();
        Long bundle = standardSku("ADMIN-BUNDLE");
        Long tomato = standardSku("ADMIN-TOMATO");
        Long potato = standardSku("ADMIN-POTATO");
        var components = new ProductBundleReplaceForm();
        components.setBundleSkuId(bundle);
        components.setVersion(jdbc.queryForObject(
                "SELECT version FROM xsy_product_sku WHERE id=?", Integer.class, bundle));
        components.setComponents(List.of(item(tomato, "2.0000"), item(potato, "3.0000")));
        bundleService.replace(components);

        SalesOrderAddForm create = new SalesOrderAddForm();
        create.setCustomerId(newCustomer());
        create.setOrderSource("ADMIN");
        create.setDeliveryWarehouseId(warehouse);
        var address = new OrderAddressForm();
        address.setReceiverName("测试收货人");
        address.setReceiverPhone("13800000000");
        address.setAddress("测试收货地址");
        create.setAddress(address);
        var orderItem = new SalesOrderItemForm();
        orderItem.setSkuId(bundle);
        orderItem.setOrderedQuantity("1.0000");
        orderItem.setManualPriceOverride(false);
        create.setItems(List.of(orderItem));

        var order = salesOrderService.createAndProgress(create, prefix + ":admin-bundle-create");
        assertThat(order.getStatus()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT mall_delivery_warehouse_id FROM xsy_sales_order WHERE id=?",
                Long.class, order.getOrderId())).isEqualTo(warehouse);
        var frozen = jdbc.queryForList(
                "SELECT component_sku_id, market_unit_price_snapshot, allocated_sale_amount_snapshot "
                + "FROM xsy_order_bundle_component WHERE order_id=? ORDER BY component_sku_id",
                order.getOrderId());
        assertThat(frozen).hasSize(2);
        BigDecimal sum = jdbc.queryForObject(
                "SELECT sum(allocated_sale_amount_snapshot) FROM xsy_order_bundle_component WHERE order_id=?",
                BigDecimal.class, order.getOrderId());
        assertThat(sum).isEqualByComparingTo(
                jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id=?",
                        BigDecimal.class, order.getOrderId()));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_inventory_reservation WHERE source_document_id=?",
                Integer.class, order.getOrderId())).isZero();
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

    private static ProductBundleReplaceForm.Component item(Long skuId, String qty) {
        var item = new ProductBundleReplaceForm.Component();
        item.setSkuId(skuId);
        item.setQuantityPerBundleSaleUnit(qty);
        return item;
    }
}
