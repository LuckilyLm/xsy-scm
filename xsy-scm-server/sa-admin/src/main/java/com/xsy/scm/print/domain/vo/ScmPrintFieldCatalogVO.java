package com.xsy.scm.print.domain.vo;

import com.xsy.scm.print.constant.ScmPrintField;
import java.util.List;
import lombok.Data;

/**
 * 某单据类型的<b>字段目录</b>：模板配置页据此渲染可选项，不硬编码字段清单。
 *
 * <p>
 * 同时回报当前调用者的金额可见性：目录里会列出金额字段（否则用户不知道有这一列）， 但 {@code amountVisible=false} 时页面必须提示「选了也不会打印出来」——
 * 这正是服务端的真实行为，藏着它只会让用户以为配置没生效。
 */
@Data
public class ScmPrintFieldCatalogVO {

    private String documentType;

    private String documentTypeLabel;

    private List<ScmPrintField> headerFields;

    private List<ScmPrintField> columns;

    private List<ScmPrintField> totals;

    /**
     * 该单据类型是否要求金额权限。
     */
    private boolean amountPermissionRequired;

    /**
     * 当前调用者是否能看到金额字段。
     */
    private boolean amountVisible;
}
