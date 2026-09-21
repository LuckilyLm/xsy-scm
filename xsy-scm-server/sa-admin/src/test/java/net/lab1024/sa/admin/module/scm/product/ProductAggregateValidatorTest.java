package net.lab1024.sa.admin.module.scm.product;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.domain.form.*;
import net.lab1024.sa.admin.module.scm.product.manager.ProductAggregateValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ProductAggregateValidatorTest {
    private final ProductAggregateValidator validator = new ProductAggregateValidator();

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
        assertThatThrownBy(() -> validator.validateSpu(form)).isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }

    @Test
    void acceptsZeroPriceAndBlankBarcodes() {
        var a = sku("A", true, "大");
        a.setMarketPrice(BigDecimal.ZERO);
        a.setBarcode(" ");
        var b = sku("B", false, "小");
        b.setBarcode(null);
        assertThatCode(() -> validator.validateSpu(form(a, b))).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyAndMissingOrMultipleDefaults() {
        rejects(form(), 40020);
        rejects(form(sku("A", false, "大")), 40021);
        rejects(form(sku("A", true, "大"), sku("B", true, "小")), 40021);
    }

    @Test
    void rejectsNormalizedCodesAndNonblankBarcodes() {
        rejects(form(sku(" a ", true, "大"), sku("A", false, "小")), 40022);
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
