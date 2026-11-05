package com.xsy.scm.common.filter;

import com.xsy.scm.common.exception.ScmBusinessException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 高级筛选只绑定查询表单支持的字段，不拼 SQL，也不改数据权限。
 */
public final class ScmAdvancedFilterCatalog {
    private ScmAdvancedFilterCatalog() {
    }

    public record Field(String key, String title, String operator, String valueType) {
    }

    public record Condition(String key, String operator, String value) {
    }

    public static Map<String, String> resolve(List<Condition> requested, List<Field> fields) {
        if (requested == null || requested.isEmpty())
            return Map.of();
        if (requested.size() > 8)
            throw invalid();
        Map<String, Field> whitelist = new HashMap<>();
        for (Field field : fields) {
            if (whitelist.putIfAbsent(field.key(), field) != null) {
                throw new IllegalStateException("重复的高级筛选字段配置");
            }
        }
        Set<String> seen = new HashSet<>();
        Map<String, String> values = new HashMap<>();
        for (Condition condition : requested) {
            if (condition == null || condition.key() == null || condition.operator() == null
                || condition.value() == null)
                throw invalid();
            Field field = whitelist.get(condition.key());
            if (field == null || !field.operator().equals(condition.operator()) || !seen.add(condition.key()))
                throw invalid();
            String value = condition.value().trim();
            if (value.isEmpty() || value.length() > 150)
                throw invalid();
            switch (field.valueType()) {
                case "ID" -> positiveId(value);
                case "BOOLEAN" -> {
                    if (!"true".equals(value) && !"false".equals(value))
                        throw invalid();
                }
                case "TEXT" -> {
                    if (value.length() > 150)
                        throw invalid();
                }
                default -> throw new IllegalStateException("未知高级筛选字段值类型");
            }
            values.put(field.key(), value);
        }
        return Map.copyOf(values);
    }

    public static Long id(Map<String, String> filters, String key, Long current) {
        String value = filters.get(key);
        if (value == null)
            return current;
        Long parsed = positiveId(value);
        if (current != null && !current.equals(parsed))
            throw invalid();
        return parsed;
    }

    public static String text(Map<String, String> filters, String key, String current) {
        String value = filters.get(key);
        if (value == null)
            return current;
        if (current != null && !current.isBlank() && !current.trim().equals(value))
            throw invalid();
        return value;
    }

    public static Boolean bool(Map<String, String> filters, String key, Boolean current) {
        String value = filters.get(key);
        if (value == null)
            return current;
        Boolean parsed = Boolean.valueOf(value);
        if (current != null && !current.equals(parsed))
            throw invalid();
        return parsed;
    }

    private static Long positiveId(String value) {
        if (!value.matches("[1-9]\\d{0,18}"))
            throw invalid();
        try {
            long id = Long.parseLong(value);
            if (id <= 0)
                throw invalid();
            return id;
        } catch (NumberFormatException ex) {
            throw invalid();
        }
    }

    private static ScmBusinessException invalid() {
        return new ScmBusinessException(VALIDATION_ERROR);
    }
}
