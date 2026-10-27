package com.xsy.scm.print.domain.vo;

import com.xsy.scm.print.domain.model.ScmPrintTemplateModel;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 打印模板（列表与详情共用）。
 */
@Data
public class ScmPrintTemplateVO {

    private Long id;

    private String documentType;

    /** 单据类型展示名，由服务端填，前端不用自带字典。 */
    private String documentTypeLabel;

    private String templateCode;

    private String templateName;

    private Boolean defaultFlag;

    private Boolean enabledFlag;

    /** 是否为当前单据客户的专属模板；仅模板选项接口会填。 */
    private Boolean customerBound;

    /** 受控模型；列表也返回，便于直接看出「这份模板打了哪几列」。 */
    private ScmPrintTemplateModel model;

    private String remark;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String updatedBy;
}
