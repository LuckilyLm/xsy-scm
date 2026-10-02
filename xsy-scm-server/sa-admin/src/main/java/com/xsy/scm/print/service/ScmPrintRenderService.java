package com.xsy.scm.print.service;

import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.print.constant.ScmPrintErrorCode;
import com.xsy.scm.print.constant.ScmPrintField;
import com.xsy.scm.print.domain.model.ScmPrintTemplateModel;
import com.xsy.scm.print.domain.vo.ScmPrintRenderColumnVO;
import com.xsy.scm.print.domain.vo.ScmPrintRenderFieldVO;
import com.xsy.scm.print.domain.vo.ScmPrintRenderVO;
import com.xsy.scm.print.support.ScmPrintSource;
import com.xsy.scm.print.support.ScmPrintSourceProvider;
import com.xsy.scm.common.exception.ScmBusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 渲染：把「一份模板 + 一张单据的数据源」变成一份**已经算好的版面**。
 *
 * <p>
 * 这里是权限与白名单的收口点，两件事都在服务端做完：
 * <ul>
 * <li><b>白名单</b>：模板只能选到类型目录里的 key（写入时已校验），渲染按模板选的 key 取值，
 * 不存在「模板里写什么就取什么」；</li>
 * <li><b>金额</b>：模板里选了金额字段、而调用者没有该单据类型的金额权限时，字段在这里被
 * 剔除并记入 {@code hiddenFields}。授权不写进模板 —— 同一份模板对不同的人渲染出不同的列，
 * 这正是「打印不绕过金额权限」的实现方式。</li>
 * </ul>
 *
 * <p>
 * 本类<b>不读库</b>：数据源由调用方先取好（因此范围判定与详情页同源），
 * 快照与实时渲染走的是同一段代码，不会出现「预览和打印不是同一张」。
 */
@Service
@RequiredArgsConstructor
public class ScmPrintRenderService {

    private final List<ScmPrintSourceProvider> providers;

    /**
     * 按单据类型取数据源实现。
     */
    public ScmPrintSourceProvider provider(ScmPrintDocumentTypeEnum type) {
        for (ScmPrintSourceProvider provider : providers) {
            if (provider.documentType() == type) {
                return provider;
            }
        }
        // 枚举里有类型、却没有对应数据源：属于部署不完整，按「暂不支持」回答而不是空表
        throw new ScmBusinessException(ScmPrintErrorCode.DOCUMENT_TYPE_UNSUPPORTED);
    }

    /**
     * 当前调用者对该单据类型是否可见金额。
     */
    public boolean amountVisible(ScmPrintDocumentTypeEnum type) {
        return !type.requiresMoneyPermission() || ScmDataScopeService.hasPermission(type.getMoneyPermission());
    }

    /**
     * 渲染一份版面。
     *
     * @param type
     *            单据类型（白名单来源）
     * @param source
     *            数据源
     * @param model
     *            已校验的模板模型
     * @param amountVisible
     *            调用者是否可见金额
     */
    public ScmPrintRenderVO render(ScmPrintDocumentTypeEnum type, ScmPrintSource source, ScmPrintTemplateModel model,
            boolean amountVisible) {
        ScmPrintRenderVO render = new ScmPrintRenderVO();
        render.setDocumentType(type.name());
        render.setBusinessNo(source.documentNo());
        render.setTitle(model.getTitle());
        render.setPaper(model.getPaper());
        render.setOrientation(model.getOrientation());
        render.setFooterNote(model.getFooterNote());
        render.setShowTotals(model.isShowTotals());

        List<String> hidden = new ArrayList<>();
        for (String key : model.getHeaderFields()) {
            ScmPrintField field = type.headerField(key);
            if (field.money() && !amountVisible) {
                hidden.add(key);
                continue;
            }
            render.getHeaderFields().add(field(field, source.headerValues().get(key)));
        }
        for (String key : model.getColumns()) {
            ScmPrintField field = type.column(key);
            if (field.money() && !amountVisible) {
                hidden.add(key);
                continue;
            }
            ScmPrintRenderColumnVO column = new ScmPrintRenderColumnVO();
            column.setKey(field.key());
            column.setLabel(field.label());
            column.setNumeric(field.numeric());
            render.getColumns().add(column);
        }
        for (Map<String, String> row : source.rows()) {
            Map<String, String> cells = new LinkedHashMap<>();
            for (ScmPrintRenderColumnVO column : render.getColumns()) {
                String value = row.get(column.getKey());
                cells.put(column.getKey(), value == null ? "" : value);
            }
            render.getRows().add(cells);
        }
        if (model.isShowTotals()) {
            // 合计按类型的全部合计字段打印：模板只控制「要不要合计行」，
            // 逐项挑选合计字段在只有一两个字段时只是多一层配置负担。
            for (ScmPrintField field : type.getTotals()) {
                if (field.money() && !amountVisible) {
                    hidden.add(field.key());
                    continue;
                }
                render.getTotals().add(field(field, source.totalValues().get(field.key())));
            }
        }
        render.setHiddenFields(hidden);
        return render;
    }

    /**
     * 版面 → 冻结数据快照。
     *
     * <p>
     * 存的是**版面本身**（列定义、行、合计、被剔除的字段），不是业务数据：重印因此不需要
     * 再认识任何一种业务单据，也不会随业务数据变化而改变。
     */
    public static Map<String, Object> toDataSnapshot(ScmPrintRenderVO render) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("businessNo", render.getBusinessNo());
        List<Map<String, Object>> header = new ArrayList<>();
        for (ScmPrintRenderFieldVO field : render.getHeaderFields()) {
            header.add(fieldMap(field));
        }
        snapshot.put("headerFields", header);
        List<Map<String, Object>> columns = new ArrayList<>();
        for (ScmPrintRenderColumnVO column : render.getColumns()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", column.getKey());
            item.put("label", column.getLabel());
            item.put("numeric", column.isNumeric());
            columns.add(item);
        }
        snapshot.put("columns", columns);
        snapshot.put("rows", render.getRows());
        List<Map<String, Object>> totals = new ArrayList<>();
        for (ScmPrintRenderFieldVO field : render.getTotals()) {
            totals.add(fieldMap(field));
        }
        snapshot.put("totals", totals);
        snapshot.put("hiddenFields", render.getHiddenFields());
        return snapshot;
    }

    /**
     * 冻结数据快照 → 版面（历史重印）。
     *
     * <p>
     * 对结构缺失一律按空处理：一份结构坏掉的快照应该退化成一个空版面并让人看见，
     * 而不是把整次重印打成 500。
     */
    public static void applyDataSnapshot(ScmPrintRenderVO render, Map<String, Object> snapshot) {
        if (snapshot == null) {
            return;
        }
        render.setBusinessNo(string(snapshot.get("businessNo")));
        for (Map<String, Object> item : maps(snapshot.get("headerFields"))) {
            ScmPrintRenderFieldVO field = new ScmPrintRenderFieldVO();
            field.setKey(string(item.get("key")));
            field.setLabel(string(item.get("label")));
            field.setValue(string(item.get("value")));
            render.getHeaderFields().add(field);
        }
        for (Map<String, Object> item : maps(snapshot.get("columns"))) {
            ScmPrintRenderColumnVO column = new ScmPrintRenderColumnVO();
            column.setKey(string(item.get("key")));
            column.setLabel(string(item.get("label")));
            column.setNumeric(Boolean.TRUE.equals(item.get("numeric")));
            render.getColumns().add(column);
        }
        for (Map<String, Object> row : maps(snapshot.get("rows"))) {
            Map<String, String> cells = new LinkedHashMap<>();
            row.forEach((key, value) -> cells.put(key, value == null ? "" : String.valueOf(value)));
            render.getRows().add(cells);
        }
        for (Map<String, Object> item : maps(snapshot.get("totals"))) {
            ScmPrintRenderFieldVO field = new ScmPrintRenderFieldVO();
            field.setKey(string(item.get("key")));
            field.setLabel(string(item.get("label")));
            field.setValue(string(item.get("value")));
            render.getTotals().add(field);
        }
        List<String> hidden = new ArrayList<>();
        if (snapshot.get("hiddenFields") instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    hidden.add(String.valueOf(item));
                }
            }
        }
        render.setHiddenFields(hidden);
    }

    private static ScmPrintRenderFieldVO field(ScmPrintField field, String value) {
        ScmPrintRenderFieldVO vo = new ScmPrintRenderFieldVO();
        vo.setKey(field.key());
        vo.setLabel(field.label());
        vo.setValue(value == null ? "" : value);
        return vo;
    }

    private static Map<String, Object> fieldMap(ScmPrintRenderFieldVO field) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", field.getKey());
        map.put("label", field.getLabel());
        map.put("value", field.getValue());
        return map;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }
}
