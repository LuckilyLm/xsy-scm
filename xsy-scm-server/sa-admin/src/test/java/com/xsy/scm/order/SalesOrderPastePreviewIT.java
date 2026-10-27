package com.xsy.scm.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.order.domain.form.SalesOrderPasteForm;
import com.xsy.scm.order.domain.vo.SalesOrderPastePreviewRowVO;
import com.xsy.scm.order.service.SalesOrderPasteService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 订单抽屉里的整单粘贴预览：只匹配商品与价格，不建单。
 *
 * <p>未匹配行必须原样返回并带错误，不能静默丢行；重复规格、单位不符、无改价权限也不能被预览放过。
 */
class SalesOrderPastePreviewIT extends ScmW5PgITBase {

    @Autowired
    private SalesOrderPasteService salesOrderPasteService;

    @Test
    @DisplayName("#1 五个商品一次粘贴全部匹配，价格按当前取价链路解析，不创建订单")
    void previewMatchesRowsWithoutCreatingOrder() {
        Long customerId = newCustomer();
        List<Long> skuIds = new ArrayList<>();
        List<SalesOrderPasteForm.Row> rows = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Long skuId = newOnShelfSku("PASTE" + i);
            skuIds.add(skuId);
            rows.add(row(skuId, "2.0000", null));
        }
        int ordersBefore = countOrders(customerId);

        var preview = salesOrderPasteService.preview(form(customerId, rows), false);

        assertThat(preview.getTotalRows()).isEqualTo(5);
        assertThat(preview.getMatchedRows()).isEqualTo(5);
        assertThat(preview.getErrorRows()).isZero();
        assertThat(preview.getRows()).allSatisfy(row -> {
            assertThat(row.isMatched()).isTrue();
            assertThat(row.getSkuId()).isNotNull();
            assertThat(row.getResolvedUnitPrice()).isEqualByComparingTo("1.2000");
            assertThat(row.getPriceSource()).isEqualTo("MARKET");
            assertThat(row.isManualPrice()).isFalse();
        });
        assertThat(preview.getRows()).extracting(SalesOrderPastePreviewRowVO::getSkuId)
            .containsExactlyElementsOf(skuIds);
        assertThat(countOrders(customerId)).isEqualTo(ordersBefore);
    }

    @Test
    @DisplayName("#2 未知商品与单位不符都留在结果里并报错，不静默丢行")
    void unmatchedRowsStayVisibleWithErrors() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("PASTE-M");
        SalesOrderPasteForm.Row unknown = new SalesOrderPasteForm.Row();
        unknown.setProductName(prefix + "不存在的商品");
        unknown.setQuantity("1.0000");

        SalesOrderPasteForm.Row badUnit = row(skuId, "2.0000", null);
        badUnit.setUnit("件");

        var preview = salesOrderPasteService.preview(form(customerId, List.of(unknown, badUnit)), false);

        assertThat(preview.getTotalRows()).isEqualTo(2);
        assertThat(preview.getErrorRows()).isEqualTo(2);
        assertThat(preview.getRows()).hasSize(2);
        assertThat(preview.getRows().get(0).getErrors())
            .extracting(error -> error.getCode()).contains("SKU_NOT_FOUND");
        assertThat(preview.getRows().get(1).getErrors())
            .extracting(error -> error.getCode()).contains("UNIT_MISMATCH");
        assertThat(preview.getRows()).allSatisfy(row -> assertThat(row.isMatched()).isFalse());
    }

    @Test
    @DisplayName("#3 同一规格重复、无改价权限、无可用价格都不能被预览放过")
    void duplicateAndPricingBoundariesAreRejected() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("PASTE-D");
        SalesOrderPasteForm.Row first = row(skuId, "1.0000", null);
        SalesOrderPasteForm.Row duplicate = row(skuId, "1.0000", null);

        var preview = salesOrderPasteService.preview(form(customerId, List.of(first, duplicate)), false);
        assertThat(preview.getRows().get(1).getErrors())
            .extracting(error -> error.getCode()).contains("SKU_DUPLICATE");

        SalesOrderPasteForm.Row manual = row(skuId, "1.0000", "2.5000");
        var forbidden = salesOrderPasteService.preview(form(customerId, List.of(manual)), false);
        assertThat(forbidden.getRows().getFirst().getErrors())
            .extracting(error -> error.getCode()).contains("PRICE_OVERRIDE_FORBIDDEN");

        var allowed = salesOrderPasteService.preview(form(customerId, List.of(manual)), true);
        assertThat(allowed.getErrorRows()).isZero();
        assertThat(allowed.getRows().getFirst().isManualPrice()).isTrue();
        assertThat(allowed.getRows().getFirst().getRawUnitPrice()).isEqualTo("2.5000");
    }

    private SalesOrderPasteForm form(Long customerId, List<SalesOrderPasteForm.Row> rows) {
        SalesOrderPasteForm form = new SalesOrderPasteForm();
        form.setCustomerId(customerId);
        form.setRows(new ArrayList<>(rows));
        return form;
    }

    private SalesOrderPasteForm.Row row(Long skuId, String quantity, String unitPrice) {
        Map<String, Object> product = jdbc.queryForMap(
            "SELECT sp.name product_name, k.spec_name, k.sale_unit FROM xsy_product_sku k "
                + "JOIN xsy_product_spu sp ON sp.id = k.spu_id WHERE k.id = ?", skuId);
        SalesOrderPasteForm.Row row = new SalesOrderPasteForm.Row();
        row.setProductName(String.valueOf(product.get("product_name")));
        row.setSpecName(String.valueOf(product.get("spec_name")));
        row.setUnit(String.valueOf(product.get("sale_unit")));
        row.setQuantity(quantity);
        row.setUnitPrice(unitPrice);
        return row;
    }

    private int countOrders(Long customerId) {
        Integer value = jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE customer_id = ?",
            Integer.class, customerId);
        return value == null ? 0 : value;
    }
}
