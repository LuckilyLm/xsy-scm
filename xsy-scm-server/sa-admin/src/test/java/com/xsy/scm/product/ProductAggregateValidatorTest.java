package com.xsy.scm.product;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.product.domain.form.*;
import com.xsy.scm.product.manager.ProductAggregateValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ProductAggregateValidatorTest {
    private final ProductAggregateValidator productAggregateValidator = new ProductAggregateValidator();

    static ProductSkuForm sku(String code, boolean primary, String spec) {
        var sku = new ProductSkuForm();
        sku.setSkuCode(code);
        sku.setDefaultFlag(primary);
        sku.setSpecValues(Map.of("规格", spec));
        sku.setMarketPrice(new BigDecimal("1.2000"));
        return sku;
    }

    static ProductSpuAddForm form(ProductSkuForm... skus) {
        var form = new ProductSpuAddForm();
        form.setSkuList(new ArrayList<>(Arrays.asList(skus)));
        return form;
    }

    void rejects(ProductSpuAddForm form, int code) {
        assertThatThrownBy(() -> productAggregateValidator.validateSpu(form)).isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }

    @Test
    void acceptsZeroPriceAndBlankBarcodes() {
        var a = sku("A", true, "大");
        a.setMarketPrice(BigDecimal.ZERO);
        a.setBarcode(" ");
        var b = sku("B", false, "小");
        b.setBarcode(null);
        assertThatCode(() -> productAggregateValidator.validateSpu(form(a, b))).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyAndMissingOrMultipleDefaults() {
        rejects(form(), 40020);
        rejects(form(sku("A", false, "大")), 40021);
        rejects(form(sku("A", true, "大"), sku("B", true, "小")), 40021);
    }

    @Test
    void rejectsNormalizedBarcodes() {
        // 商品规格编码的唯一性由 uk_product_sku_code 唯一索引兜底，ProductSpuService 把
        // 约束冲突翻译成 40022；聚合校验器只负责条码/规格/价格这类行内判据，
        // 这里不再断言「 a 」与「A」在内存里被判重。
        var a = sku("A", true, "大");
        var b = sku("B", false, "小");
        a.setBarcode(" 123 ");
        b.setBarcode("123");
        rejects(form(a, b), 40023);
    }

    @Test
    void rejectsNormalizedSpecificationsAndNegativeOrMissingPrice() {
        var a = sku("A", true, "大");
        var b = sku("B", false, "小");
        a.setSpecValues(Map.of(" SIZE ", " LARGE ", "color", " RED"));
        b.setSpecValues(Map.of("Color", "red ", "size", "large"));
        rejects(form(a, b), 40024);
        a.setMarketPrice(new BigDecimal("-0.0001"));
        rejects(form(a), 40025);
        a.setMarketPrice(null);
        rejects(form(a), 40025);
    }

    @Test
    void rejectsMultiplePrimaryImagesAndRepeatedFileKeys() {
        var form = form(sku("A", true, "大"));
        var a = new ProductImageForm();
        var b = new ProductImageForm();
        a.setFileKey("a");
        b.setFileKey("b");
        a.setPrimaryFlag(true);
        b.setPrimaryFlag(true);
        form.setImages(List.of(a, b));
        rejects(form, 40026);
        b.setPrimaryFlag(false);
        b.setFileKey("a");
        rejects(form, 40026);
    }
}
