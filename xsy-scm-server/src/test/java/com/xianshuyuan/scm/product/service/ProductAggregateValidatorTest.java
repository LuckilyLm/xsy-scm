package com.xianshuyuan.scm.product.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.product.dto.ProductSaveRequest;
import com.xianshuyuan.scm.product.dto.ProductSkuSaveRequest;
import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.product.entity.ShelfStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductAggregateValidatorTest {

    private ProductAggregateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ProductAggregateValidator();
    }

    @Test
    void acceptsValidSpuWithOneDefaultSku() {
        assertThatCode(() -> validator.validate(product(List.of(sku(
            null, "SKU-APPLE-JIN", "6900000000011", Map.of("计价方式", "按斤"), true,
            new BigDecimal("6.9800")
        ))))).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptySkuList() {
        assertThatThrownBy(() -> validator.validate(product(List.of())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("至少一个 SKU");
    }

    @Test
    void rejectsMoreThanOneDefaultSku() {
        assertThatThrownBy(() -> validator.validate(product(List.of(
            sku(1L, "SKU-A", null, Map.of("包装", "散装"), true, BigDecimal.ONE),
            sku(2L, "SKU-B", null, Map.of("包装", "礼盒"), true, BigDecimal.TEN)
        ))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("一个默认 SKU");
    }

    @Test
    void rejectsDuplicateNormalizedSkuCode() {
        assertThatThrownBy(() -> validator.validate(product(List.of(
            sku(1L, "sku-a", null, Map.of("包装", "散装"), true, BigDecimal.ONE),
            sku(2L, " SKU-A ", null, Map.of("包装", "礼盒"), false, BigDecimal.TEN)
        ))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("SKU 编码重复");
    }

    @Test
    void rejectsDuplicateNonBlankBarcode() {
        assertThatThrownBy(() -> validator.validate(product(List.of(
            sku(1L, "SKU-A", " 69001 ", Map.of("包装", "散装"), true, BigDecimal.ONE),
            sku(2L, "SKU-B", "69001", Map.of("包装", "礼盒"), false, BigDecimal.TEN)
        ))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("条码重复");
    }

    @Test
    void rejectsDuplicateSpecificationCombination() {
        assertThatThrownBy(() -> validator.validate(product(List.of(
            sku(1L, "SKU-A", null, Map.of("重量", "5斤", "包装", "礼盒"), true, BigDecimal.ONE),
            sku(2L, "SKU-B", null, Map.of(" 包装 ", " 礼盒 ", "重量", "5斤"), false, BigDecimal.TEN)
        ))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("规格组合重复");
    }

    @Test
    void rejectsNegativeMarketPrice() {
        assertThatThrownBy(() -> validator.validate(product(List.of(sku(
            null, "SKU-A", null, Map.of(), true, new BigDecimal("-0.0001")
        )))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("市场价");
    }

    private static ProductSaveRequest product(List<ProductSkuSaveRequest> skus) {
        return new ProductSaveRequest(
            null, "SPU-APPLE", "红富士苹果", "苹果", 30L,
            "演示商品", ShelfStatus.ON_SHELF, skus
        );
    }

    private static ProductSkuSaveRequest sku(
        Long id,
        String code,
        String barcode,
        Map<String, String> specs,
        boolean defaultSku,
        BigDecimal marketPrice
    ) {
        return new ProductSkuSaveRequest(
            id, id == null ? null : 0, code, barcode, code, specs, "斤",
            ProductType.NON_STANDARD, marketPrice, ShelfStatus.ON_SHELF,
            defaultSku, 10
        );
    }
}
