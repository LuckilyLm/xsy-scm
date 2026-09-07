package com.xianshuyuan.scm.product.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.product.dto.ProductSaveRequest;
import com.xianshuyuan.scm.product.dto.ProductSkuSaveRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class ProductAggregateValidator {

    public void validate(ProductSaveRequest request) {
        if (request.skus() == null || request.skus().isEmpty()) {
            throw new BusinessException(ProductErrorCodes.SKU_REQUIRED);
        }
        long defaults = request.skus().stream().filter(ProductSkuSaveRequest::defaultSku).count();
        if (defaults != 1) {
            throw new BusinessException(ProductErrorCodes.DEFAULT_SKU_INVALID);
        }

        Set<String> codes = new HashSet<>();
        Set<String> barcodes = new HashSet<>();
        Set<Map<String, String>> specifications = new HashSet<>();
        for (ProductSkuSaveRequest sku : request.skus()) {
            if (!codes.add(normalizeCode(sku.skuCode()))) {
                throw new BusinessException(ProductErrorCodes.SKU_CODE_DUPLICATE);
            }
            String barcode = trimToNull(sku.barcode());
            if (barcode != null && !barcodes.add(barcode)) {
                throw new BusinessException(ProductErrorCodes.SKU_BARCODE_DUPLICATE);
            }
            if (!specifications.add(normalizeSpecifications(sku.specValues()))) {
                throw new BusinessException(ProductErrorCodes.SKU_SPEC_DUPLICATE);
            }
            if (sku.marketPrice() == null || sku.marketPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ProductErrorCodes.SKU_PRICE_INVALID);
            }
        }
    }

    public static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static Map<String, String> normalizeSpecifications(Map<String, String> values) {
        Map<String, String> normalized = new TreeMap<>();
        if (values == null) {
            return normalized;
        }
        values.forEach((key, value) -> normalized.put(
                key == null ? "" : key.trim().toLowerCase(Locale.ROOT),
                value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
        ));
        return normalized;
    }
}
