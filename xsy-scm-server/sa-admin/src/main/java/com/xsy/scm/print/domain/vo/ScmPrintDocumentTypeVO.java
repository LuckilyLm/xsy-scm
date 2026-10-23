package com.xsy.scm.print.domain.vo;

import lombok.Data;

/**
 * 一个可配置打印的单据类型，供前端类型下拉使用。
 */
@Data
public class ScmPrintDocumentTypeVO {

    private String documentType;

    private String documentTypeLabel;

    /** 该类型是否要求金额权限。 */
    private boolean amountPermissionRequired;

    /** 当前调用者是否可见金额。 */
    private boolean amountVisible;
}
