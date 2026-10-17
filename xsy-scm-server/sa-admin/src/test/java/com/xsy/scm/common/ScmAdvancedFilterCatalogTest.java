package com.xsy.scm.common;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Condition;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Field;
import com.xsy.scm.product.domain.form.ProductSpuQueryForm;
import com.xsy.scm.product.support.ProductAdvancedFilters;
import com.xsy.scm.report.domain.form.ScmPurchaseReportQueryForm;
import com.xsy.scm.report.domain.form.ScmSalesReportQueryForm;
import com.xsy.scm.report.support.ScmReportAdvancedFilters;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScmAdvancedFilterCatalogTest {
    private static final List<Field> FIELDS = List.of(
            new Field("categoryId", "分类编号", "EQ", "ID"),
            new Field("keyword", "商品关键字", "CONTAINS", "TEXT"),
            new Field("hasBarcode", "有条码", "EQ", "BOOLEAN"));

    @Test
    void onlyKnownFieldsAndSupportedOperatorsCanBindToExistingForm() {
        var values = ScmAdvancedFilterCatalog.resolve(List.of(
                new Condition("categoryId", "EQ", "1234567890123456"),
                new Condition("keyword", "CONTAINS", "青菜")), FIELDS);
        assertThat(ScmAdvancedFilterCatalog.id(values, "categoryId", null)).isEqualTo(1234567890123456L);
        assertThat(ScmAdvancedFilterCatalog.text(values, "keyword", null)).isEqualTo("青菜");
        assertThatThrownBy(() -> ScmAdvancedFilterCatalog.resolve(
                List.of(new Condition("keyword", "EQ", "青菜")), FIELDS))
                .isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> ScmAdvancedFilterCatalog.resolve(
                List.of(new Condition("secret", "EQ", "1")), FIELDS))
                .isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> ScmAdvancedFilterCatalog.resolve(
                List.of(new Condition("categoryId", "EQ", "1 OR 1=1")), FIELDS))
                .isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> ScmAdvancedFilterCatalog.resolve(
                List.of(new Condition("hasBarcode", "EQ", "yes")), FIELDS))
                .isInstanceOf(ScmBusinessException.class);
    }

    @Test
    void duplicateAndConflictingExistingConditionsFailClosed() {
        assertThatThrownBy(() -> ScmAdvancedFilterCatalog.resolve(List.of(
                new Condition("keyword", "CONTAINS", "青菜"),
                new Condition("keyword", "CONTAINS", "白菜")), FIELDS))
                .isInstanceOf(ScmBusinessException.class);
        var values = ScmAdvancedFilterCatalog.resolve(
                List.of(new Condition("categoryId", "EQ", "7")), FIELDS);
        assertThatThrownBy(() -> ScmAdvancedFilterCatalog.id(values, "categoryId", 8L))
                .isInstanceOf(ScmBusinessException.class);
        assertThat(ScmAdvancedFilterCatalog.id(values, "categoryId", 7L)).isEqualTo(7L);
    }

    @Test
    void domainAdaptersReuseOfficialQueryFilters() {
        var product = new ProductSpuQueryForm();
        product.setAdvancedFilters(List.of(new Condition("hasBarcode", "EQ", "false")));
        ProductAdvancedFilters.apply(product);
        assertThat(product.getHasBarcode()).isFalse();

        var sales = new ScmSalesReportQueryForm();
        sales.setAdvancedFilters(List.of(new Condition("sellerId", "EQ", "27")));
        ScmReportAdvancedFilters.salesOrder(sales);
        assertThat(sales.getSellerId()).isEqualTo(27L);

        var purchase = new ScmPurchaseReportQueryForm();
        purchase.setAdvancedFilters(List.of(new Condition("warehouseId", "EQ", "13")));
        ScmReportAdvancedFilters.purchaseProduct(purchase);
        assertThat(purchase.getWarehouseId()).isEqualTo(13L);
    }
}
