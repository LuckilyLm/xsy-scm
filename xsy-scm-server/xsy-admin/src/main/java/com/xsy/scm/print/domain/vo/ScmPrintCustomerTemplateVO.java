package com.xsy.scm.print.domain.vo;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 客户专属模板绑定（列表用），同时带出客户与模板名称，前端不必再逐条查询。
 */
@Data
public class ScmPrintCustomerTemplateVO {

    private Long id;

    private Long customerId;

    private String customerName;

    private String documentType;

    private String documentTypeLabel;

    private Long templateId;

    private String templateCode;

    private String templateName;

    /** 绑定模板当前是否启用；停用后打印会回退到该类型默认模板。 */
    private Boolean templateEnabled;

    private Integer version;

    private OffsetDateTime updatedAt;
}
