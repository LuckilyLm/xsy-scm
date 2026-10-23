package com.xsy.scm.report.support;

import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Field;
import com.xsy.scm.report.domain.form.ScmPurchaseReportQueryForm;
import com.xsy.scm.report.domain.form.ScmSalesReportQueryForm;

import java.util.List;

/**
 * 只允许把动态条件映射到正式报表已有的范围收窄字段。
 */
public final class ScmReportAdvancedFilters {
    private ScmReportAdvancedFilters() {
    }

    public static final List<Field> SALES_ORDER = List.of(new Field("customerId", "客户编号", "EQ", "ID"),
        new Field("sellerId", "销售员编号", "EQ", "ID"), new Field("categoryId", "商品分类编号", "EQ", "ID"),
        new Field("keyword", "商品关键字", "CONTAINS", "TEXT"));

    public static final List<Field> PURCHASE_PRODUCT = List.of(new Field("supplierId", "供应商编号", "EQ", "ID"),
        new Field("purchaserId", "采购员编号", "EQ", "ID"), new Field("warehouseId", "仓库编号", "EQ", "ID"),
        new Field("keyword", "商品关键字", "CONTAINS", "TEXT"));

    public static void salesOrder(ScmSalesReportQueryForm form) {
        var values = ScmAdvancedFilterCatalog.resolve(form.getAdvancedFilters(), SALES_ORDER);
        form.setCustomerId(ScmAdvancedFilterCatalog.id(values, "customerId", form.getCustomerId()));
        form.setSellerId(ScmAdvancedFilterCatalog.id(values, "sellerId", form.getSellerId()));
        form.setCategoryId(ScmAdvancedFilterCatalog.id(values, "categoryId", form.getCategoryId()));
        form.setKeyword(ScmAdvancedFilterCatalog.text(values, "keyword", form.getKeyword()));
    }

    public static void purchaseProduct(ScmPurchaseReportQueryForm form) {
        var values = ScmAdvancedFilterCatalog.resolve(form.getAdvancedFilters(), PURCHASE_PRODUCT);
        form.setSupplierId(ScmAdvancedFilterCatalog.id(values, "supplierId", form.getSupplierId()));
        form.setPurchaserId(ScmAdvancedFilterCatalog.id(values, "purchaserId", form.getPurchaserId()));
        form.setWarehouseId(ScmAdvancedFilterCatalog.id(values, "warehouseId", form.getWarehouseId()));
        form.setKeyword(ScmAdvancedFilterCatalog.text(values, "keyword", form.getKeyword()));
    }
}
