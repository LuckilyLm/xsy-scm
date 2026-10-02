package com.xsy.scm.print.domain.vo;

import lombok.Data;

/**
 * 一个「可配置打印」的单据类型（供前端类型下拉使用，避免前端硬编码类型清单）。
 */
@Data
public class ScmPrintDocumentTypeVO {

    private String documentType;

    private String documentTypeLabel;

    /**
     * 该类型是否要求金额权限。
     */
    private boolean amountPermissionRequired;

    /**
     * 当前调用者是否可见金额。
     */
    private boolean amountVisible;
}
