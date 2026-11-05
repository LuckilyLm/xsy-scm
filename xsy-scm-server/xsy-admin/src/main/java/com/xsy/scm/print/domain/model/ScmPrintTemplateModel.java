package com.xsy.scm.print.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 打印模板只支持标题、纸张、列、表头、合计和页脚，不支持脚本、HTML 或自由表达式。结构校验由统一校验器负责。
 */
@Data
public class ScmPrintTemplateModel {

    /** 单据标题，纯文本。 */
    private String title;

    /** 纸张：{@code A4} / {@code TICKET_80}。 */
    private String paper;

    /** 方向：{@code PORTRAIT} / {@code LANDSCAPE}。 */
    private String orientation;

    /** 表头字段 key（单据类型允许范围内）。 */
    private List<String> headerFields = new ArrayList<>();

    /** 明细列 key（单据类型允许范围内）。 */
    private List<String> columns = new ArrayList<>();

    /** 是否打印整单合计行。 */
    private boolean showTotals;

    /** 页脚备注，纯文本，可为空。 */
    private String footerNote;

    /** 转成 JSONB 列的形状；字段顺序固定，便于比对和复现。 */
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

    /** 从 JSONB 列还原；结构缺失按空处理，默认值由种子数据给出。 */
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
