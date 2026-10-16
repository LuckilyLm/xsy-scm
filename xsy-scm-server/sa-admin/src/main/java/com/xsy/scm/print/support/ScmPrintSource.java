package com.xsy.scm.print.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 打印来源把不同单据转换成统一字段集合，数量和金额已按后端口径格式化，渲染层不重复计算。 */
public record ScmPrintSource(String documentNo, Map<String, String> headerValues, List<Map<String, String>> rows,
        Map<String, String> totalValues) {

    /**
     * 可变的行容器（数据源逐行 put 时用）。
     */
    public static Map<String, String> row() {
        return new LinkedHashMap<>();
    }

    /**
     * 可变的字段容器。
     */
    public static Map<String, String> values() {
        return new LinkedHashMap<>();
    }
}
