package com.xsy.scm.print.permission;

/**
 * 打印中心发布的权限码。
 *
 * <p>
 * 只有<b>模板维护</b>与<b>打印记录查看</b>在这里。打印一张业务单据本身不在这里授权 —— 它要求的是该单据自己的查看权（例如采购单要 {@code scm:purchase:query}），
 * 否则「模板维护权」会变成一条读到任意业务单据的旁路。
 */
public final class ScmPrintPermission {

    public static final String TEMPLATE_QUERY = "scm:print:template:query";

    public static final String TEMPLATE_ADD = "scm:print:template:add";

    public static final String TEMPLATE_UPDATE = "scm:print:template:update";

    public static final String TEMPLATE_DELETE = "scm:print:template:delete";

    public static final String RECORD_QUERY = "scm:print:record:query";

    private ScmPrintPermission() {
    }
}
