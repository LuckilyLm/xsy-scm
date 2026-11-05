package com.xsy.scm.product.support;

import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Field;
import com.xsy.scm.product.domain.form.ProductSpuQueryForm;

import java.util.List;

/**
 * 商品动态筛选复用正式商品查询和导出表单，不创建任意条件 SQL。
 */
public final class ProductAdvancedFilters {
    private ProductAdvancedFilters() {
    }

    public static final List<Field> FIELDS = List.of(new Field("keyword", "商品关键字", "CONTAINS", "TEXT"),
        new Field("categoryId", "商品分类编号", "EQ", "ID"), new Field("hasPrimaryImage", "有商品主图", "EQ", "BOOLEAN"),
        new Field("hasBarcode", "有商品条码", "EQ", "BOOLEAN"));

    public static void apply(ProductSpuQueryForm form) {
        var values = ScmAdvancedFilterCatalog.resolve(form.getAdvancedFilters(), FIELDS);
        form.setKeyword(ScmAdvancedFilterCatalog.text(values, "keyword", form.getKeyword()));
        form.setCategoryId(ScmAdvancedFilterCatalog.id(values, "categoryId", form.getCategoryId()));
        form.setHasPrimaryImage(ScmAdvancedFilterCatalog.bool(values, "hasPrimaryImage", form.getHasPrimaryImage()));
        form.setHasBarcode(ScmAdvancedFilterCatalog.bool(values, "hasBarcode", form.getHasBarcode()));
    }
}
