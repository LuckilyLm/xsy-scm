package com.xsy.scm.print.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 打印渲染结果已包含表头、列顺序、单元格文本与合计。前端只负责排版，金额权限由服务端处理；历史重印使用冻结内容。
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

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String printedBy;
}
