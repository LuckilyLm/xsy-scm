package com.xsy.scm.print.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一张待打印单据的**规范化来源**：所有单据类型都被摊平成同一种形状，模板与渲染层因此不需要
 * 认识任何一个业务 VO。
 *
 * <p>
 * 值一律是**已格式化的字符串**：数量与金额保持后端既有的四位定点字符串口径，
 * 日期时间由数据源自己格式化。渲染层不做数值计算，也就不会出现「前端算出另一个数」。
 *
 * <p>
 * 只有白名单里出现过的 key 才会被放进 map；模板选了不存在的 key 在校验阶段就已经被拒绝，
 * 因此渲染时取不到值只可能是数据本身为空（例如备注为空）。
 *
 * @param documentNo
 *            业务单号（采购单号等），用于记录与标题展示
 * @param headerValues
 *            表头字段：key → 文本值
 * @param rows
 *            明细行：每行 key → 文本值
 * @param totalValues
 *            合计字段：key → 文本值
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
