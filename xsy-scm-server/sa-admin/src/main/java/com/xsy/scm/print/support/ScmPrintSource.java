package com.xsy.scm.print.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一张待打印单据的规范化来源：所有单据类型被摊平成同一形状，模板与渲染层因此不需要认识任何业务 VO。
 *
 * <p>
 * 值一律是已格式化的字符串（数量与金额保持后端四位定点字符串口径，日期时间由数据源自己格式化），渲染层不做数值计算。 只有白名单里的 key 会进 map；模板选了不存在的 key
 * 在校验阶段已被拒绝，因此渲染时取不到值只可能是数据本身为空。
 */
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
