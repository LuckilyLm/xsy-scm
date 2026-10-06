package com.xsy.scm.print.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 打印模板的<b>受控模型</b>（存进 {@code scm_print_template.model} 的形状）。
 *
 * <p>
 * 它刻意不是一个「版式描述语言」：只有标题、纸张、方向、表头字段、明细列、是否合计、页脚备注。 没有位置、字号、HTML、脚本，也没有表达式 —— 能表达的版式就是「一张表 + 一行表头 + 一行页脚」，
 * 因此不存在需要求值器才能渲染的模板，也不存在模板里能执行代码的入口。
 *
 * <p>
 * {@link #fromMap} 对结构缺失一律按空处理（例如 {@code columns} 不是数组）， 把「结构不对」交给校验器统一回答，而不是在这里抛一个说不清位置的异常。
 */
@Data
public class ScmPrintTemplateModel {

    /**
     * 单据标题；纯文本。
     */
    private String title;

    /**
     * 纸张：{@code A4} / {@code TICKET_80}。
     */
    private String paper;

    /**
     * 方向：{@code PORTRAIT} / {@code LANDSCAPE}。
     */
    private String orientation;

    /**
     * 表头字段 key（单据类型白名单内）。
     */
    private List<String> headerFields = new ArrayList<>();

    /**
     * 明细列 key（单据类型白名单内）。
     */
    private List<String> columns = new ArrayList<>();

    /**
     * 是否打印整单合计行。
     */
    private boolean showTotals;

    /**
     * 页脚备注；纯文本，可为空。
     */
    private String footerNote;

    /**
     * 转成 JSONB 列的形状。字段顺序固定，便于人工比对与日志复现。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", title);
        map.put("paper", paper);
        map.put("orientation", orientation);
        map.put("headerFields", List.copyOf(headerFields));
        map.put("columns", List.copyOf(columns));
        map.put("showTotals", showTotals);
        map.put("footerNote", footerNote);
        return map;
    }

    /**
     * 从 JSONB 列还原。结构缺失按空处理，不做隐式默认（默认值属于种子数据，不属于解析）。
     */
    public static ScmPrintTemplateModel fromMap(Map<String, Object> map) {
        ScmPrintTemplateModel model = new ScmPrintTemplateModel();
        if (map == null) {
            return model;
        }
        model.setTitle(text(map.get("title")));
        model.setPaper(text(map.get("paper")));
        model.setOrientation(text(map.get("orientation")));
        model.setHeaderFields(strings(map.get("headerFields")));
        model.setColumns(strings(map.get("columns")));
        model.setShowTotals(Boolean.TRUE.equals(map.get("showTotals")));
        model.setFooterNote(text(map.get("footerNote")));
        return model;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }
}
