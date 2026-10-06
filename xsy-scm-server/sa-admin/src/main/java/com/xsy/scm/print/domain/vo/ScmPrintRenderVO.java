package com.xsy.scm.print.domain.vo;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 打印渲染结果（预览与正式打印返回同一形状）。
 *
 * <p>
 * 它是一份<b>已经算好的版面</b>：列定义、行单元格、表头字段、合计、被剔除的字段都在里面。前端只做「按列顺序取值 + 转义 + 排版」，不再自己决定打印哪些列 —— 否则权限剔除与
 * 白名单就只在前端生效，绕过页面直接调接口即可拿到全部字段。
 *
 * <p>
 * {@code rows} 的每个元素以<b>列 key</b> 为键；单元格是字符串，四位定点数量与金额原样透传。
 *
 * <p>
 * {@code frozen=true} 表示这份结果来自冻结快照（历史重印），此时 {@code recordId} / {@code printedAt} / {@code printedBy} 非空，且数字不再随业务数据变化。
 */
@Data
public class ScmPrintRenderVO {

    private String documentType;

    private Long businessId;

    private String businessNo;

    private Long templateId;

    private String templateCode;

    private String templateName;

    private Integer templateVersion;

    private String title;

    private String paper;

    private String orientation;

    private String footerNote;

    private boolean showTotals;

    private List<ScmPrintRenderFieldVO> headerFields = new ArrayList<>();

    private List<ScmPrintRenderColumnVO> columns = new ArrayList<>();

    private List<Map<String, String>> rows = new ArrayList<>();

    private List<ScmPrintRenderFieldVO> totals = new ArrayList<>();

    /**
     * 模板里选了、但因调用者金额权限不足被剔除的字段 key。
     *
     * <p>
     * 必须回报：静默少一列会让人以为模板没配好，而实际原因是他自己没有金额权限。
     */
    private List<String> hiddenFields = new ArrayList<>();

    /**
     * 是否为冻结快照（历史重印）。
     */
    private boolean frozen;

    private Long recordId;

    private OffsetDateTime printedAt;

    private String printedBy;
}
