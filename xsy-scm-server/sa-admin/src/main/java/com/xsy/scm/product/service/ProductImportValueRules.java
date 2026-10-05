package com.xsy.scm.product.service;

import com.xsy.scm.product.domain.vo.ProductImportErrorVO;
import com.xsy.scm.product.domain.vo.ProductImportResultVO;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/** Shared cell normalization and validation rules for product workbook imports. */
final class ProductImportValueRules {

    private static final int MAX_ERRORS = 1000;

    private ProductImportValueRules() {
    }

    static Long parseLong(String value) {
        var trimmedValue = trim(value);
        if (trimmedValue == null) {
            return null;
        }
        try {
            return Long.valueOf(trimmedValue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static Integer parseInteger(String value) {
        var trimmedValue = trim(value);
        if (trimmedValue == null) {
            return null;
        }
        try {
            return Integer.valueOf(trimmedValue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static void positiveId(ProductImportResultVO result, int rowNumber, String key, String column, String value) {
        var trimmedValue = trim(value);
        if (trimmedValue != null && (parseLong(trimmedValue) == null || parseLong(trimmedValue) <= 0)) {
            addError(result, rowNumber, key, column, "ID_INVALID", column + "必须是正整数");
        }
    }

    static void nonNegative(ProductImportResultVO result, int rowNumber, String key, String column, String value) {
        var trimmedValue = trim(value);
        if (trimmedValue == null) {
            return;
        }
        var number = parseInteger(trimmedValue);
        if (number == null || number < 0) {
            addError(result, rowNumber, key, column, "INT_INVALID", column + "必须是 0 以上的整数");
        }
    }

    static void required(ProductImportResultVO result, int rowNumber, String key, String column, String value) {
        if (trim(value) == null) {
            addError(result, rowNumber, key, column, "REQUIRED", column + "不能为空");
        }
    }

    static void length(ProductImportResultVO result, int rowNumber, String key, String column, String value, int max) {
        var trimmedValue = trim(value);
        if (trimmedValue != null && trimmedValue.length() > max) {
            addError(result, rowNumber, key, column, "TOO_LONG", column + "不能超过 " + max + " 个字符");
        }
    }

    static void enumValue(ProductImportResultVO result, int rowNumber, String key, String column, String value,
            Set<String> allowed) {
        var trimmedValue = trim(value);
        if (trimmedValue != null && !allowed.contains(trimmedValue)) {
            addError(result, rowNumber, key, column, "ENUM_INVALID", column + "取值必须是 " + allowed);
        }
    }

    static void decimal(ProductImportResultVO result, int rowNumber, String key, String column, String value,
            boolean positive) {
        var trimmedValue = trim(value);
        if (trimmedValue == null) {
            return;
        }
        try {
            if (!trimmedValue.matches("[0-9]{1,14}(\\.[0-9]{1,4})?")) {
                throw new NumberFormatException();
            }
            var number = new BigDecimal(trimmedValue);
            if (positive ? number.signum() <= 0 : number.signum() < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            addError(result, rowNumber, key, column, "DECIMAL_INVALID",
                    column + "必须是" + (positive ? "大于零的" : "非负") + "四位以内小数");
        }
    }

    static void integer(ProductImportResultVO result, int rowNumber, String key, String column, String value, int min,
            int max) {
        var trimmedValue = trim(value);
        if (trimmedValue == null) {
            return;
        }
        try {
            var number = Integer.parseInt(trimmedValue);
            if (number < min || number > max) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            addError(result, rowNumber, key, column, "INT_INVALID", column + "必须是 " + min + "~" + max + " 的整数");
        }
    }

    static void same(ProductImportResultVO result, int rowNumber, String key, String column, String expected,
            String actual) {
        if (!Objects.equals(trim(expected), trim(actual))) {
            addError(result, rowNumber, key, column, "HEADER_CONFLICT", "同一商品的" + column + "必须一致");
        }
    }

    static String trim(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static void addError(ProductImportResultVO result, int rowNumber, String key, String column, String code,
            String message) {
        result.setTotalErrors(result.getTotalErrors() + 1);
        if (result.getErrors().size() < MAX_ERRORS) {
            result.getErrors().add(new ProductImportErrorVO(rowNumber, key, column, code, message));
        }
    }
}
