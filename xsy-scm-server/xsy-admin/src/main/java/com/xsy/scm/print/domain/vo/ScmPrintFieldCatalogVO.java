package com.xsy.scm.print.domain.vo;

import com.xsy.scm.print.constant.ScmPrintField;

import java.util.List;

import lombok.Data;

/**
 * 字段目录，供模板配置时选择。金额字段能否打印由当前用户权限决定，页面上应提示无权限的字段不会出现在打印结果里。
 */
@Data
public class ScmPrintFieldCatalogVO {

    private String documentType;

    private String documentTypeLabel;

    private List<ScmPrintField> headerFields;

    private List<ScmPrintField> columns;

    private List<ScmPrintField> totals;

    /** 该单据类型是否要求金额权限。 */
    private boolean amountPermissionRequired;

    /** 当前调用者是否能看到金额字段。 */
    private boolean amountVisible;
}
