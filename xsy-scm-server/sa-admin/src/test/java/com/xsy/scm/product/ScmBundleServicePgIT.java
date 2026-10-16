package com.xsy.scm.product;

import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.service.OrderBundleSnapshotService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V140 真实 PostgreSQL 上经由商品聚合创建标准规格，再执行组合组成替换，
 * 验证乐观版本、活动唯一索引与历史组成软删除。用例均在事务中回滚。
 */
class ScmBundleServicePgIT extends ScmW3PgITBase {

    @Autowired
    private ProductBundleService bundleService;

    @Autowired
    private OrderBundleSnapshotService snapshotService;

    @Test
    void replaceBundleComponentsPersistsAndIncrementsSkuVersion() {
        Long bundle = createStandardSku("BUNDLE");
        Long apple = createStandardSku("APPLE");
        Long pear = createStandardSku("PEAR");

        int version = skuVersion(bundle);
        bundleService.replace(form(bundle, version, item(apple, "2.0000"), item(pear, "3.0000")));

        assertThat(bundleService.components(bundle)).hasSize(2);
        assertThat(skuVersion(bundle)).isEqualTo(version + 1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_product_bundle_component "
                + "WHERE bundle_sku_id=? AND deleted=FALSE", Integer.class, bundle)).isEqualTo(2);

        bundleService.replace(form(bundle, version + 1, item(pear, "1.0000")));

        assertThat(bundleService.components(bundle)).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_product_bundle_component WHERE bundle_sku_id=? AND deleted=TRUE",
                Integer.class, bundle)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_product_bundle_component WHERE bundle_sku_id=? AND deleted=FALSE",
                Integer.class, bundle)).isEqualTo(1);
    }

    @Test
    void realBundleConfigurationFreezesAllocationWithoutReservingStockAtConfirmation() {
        Long bundle = createStandardSku("FREEZE_BUNDLE");
        Long apple = createStandardSku("FREEZE_APPLE");
        Long pear = createStandardSku("FREEZE_PEAR");
        bundleService.replace(form(bundle, skuVersion(bundle), item(apple, "2.0000"), item(pear, "3.0000")));
        Long warehouse = jdbc.queryForObject(
                "SELECT id FROM xsy_warehouse WHERE deleted=FALSE ORDER BY id LIMIT 1", Long.class);

        SalesOrderItemEntity orderItem = new SalesOrderItemEntity();
        orderItem.setOrderId(990001L);
        orderItem.setId(990002L);
        orderItem.setSkuId(bundle);
        orderItem.setActualQuantity(new BigDecimal("1.0000"));
        orderItem.setLockedUnitPrice(new BigDecimal("80.0000"));

        assertThat(snapshotService.freeze(orderItem, warehouse)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT sum(allocated_sale_amount_snapshot) FROM xsy_order_bundle_component "
                + "WHERE order_item_id=990002", BigDecimal.class)).isEqualByComparingTo("80.0000");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_order_bundle_component "
                + "WHERE order_item_id=990002 AND warehouse_id=? AND market_unit_price_snapshot=10.0000",
                Integer.class, warehouse)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_inventory_reservation WHERE source_document_id=990001",
                Integer.class)).isZero();
    }

    @Test
    void duplicateComponentIsRejectedBeforeItWritesPartialConfiguration() {
        Long bundle = createStandardSku("DUP_BUNDLE");
        Long apple = createStandardSku("DUP_APPLE");

        assertThatThrownBy(() -> bundleService.replace(
                form(bundle, skuVersion(bundle), item(apple, "1.0000"), item(apple, "2.0000"))))
                .isInstanceOf(ScmBusinessException.class);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_product_bundle_component WHERE bundle_sku_id=? AND deleted=FALSE",
                Integer.class, bundle)).isZero();
    }

    @Test
    void nestedBundleIsRejected() {
        Long outer = createStandardSku("OUTER");
        Long inner = createStandardSku("INNER");
        Long leaf = createStandardSku("LEAF");
        bundleService.replace(form(inner, skuVersion(inner), item(leaf, "1.0000")));

        assertThatThrownBy(() -> bundleService.replace(
                form(outer, skuVersion(outer), item(inner, "1.0000"))))
                .isInstanceOf(ScmBusinessException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_product_bundle_component WHERE bundle_sku_id=? AND deleted=FALSE",
                Integer.class, outer)).isZero();
    }

    private Long createStandardSku(String suffix) {
        ProductSpuAddForm spu = new ProductSpuAddForm();
        spu.setSpuCode(prefix + suffix);
        spu.setName(prefix + suffix + "商品");
        spu.setStatus("ON_SHELF");
        spu.setCategoryId(jdbc.queryForObject(
                "SELECT id FROM xsy_product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE",
                Long.class));

        ProductSkuForm sku = new ProductSkuForm();
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

    private int skuVersion(Long skuId) {
        return jdbc.queryForObject("SELECT version FROM xsy_product_sku WHERE id=?", Integer.class, skuId);
    }

    private static ProductBundleReplaceForm form(Long bundleSkuId, int version,
            ProductBundleReplaceForm.Component... components) {
        ProductBundleReplaceForm form = new ProductBundleReplaceForm();
        form.setBundleSkuId(bundleSkuId);
        form.setVersion(version);
        form.setComponents(List.of(components));
        return form;
    }

    private static ProductBundleReplaceForm.Component item(Long skuId, String quantity) {
        ProductBundleReplaceForm.Component item = new ProductBundleReplaceForm.Component();
        item.setSkuId(skuId);
        item.setQuantityPerBundleSaleUnit(quantity);
        return item;
    }
}
