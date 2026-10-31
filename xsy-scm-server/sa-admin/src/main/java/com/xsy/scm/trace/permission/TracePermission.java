package com.xsy.scm.trace.permission;

/**
 * 包装码接口的权限标识；与迁移里发布的菜单 api_perms 逐字对应。
 */
public final class TracePermission {

    private TracePermission() {
    }

    /** 包装码列表与回放查询。 */
    public static final String UNIT_QUERY = "scm:inventory:unit:query";

    /** 按批次发码。 */
    public static final String UNIT_ISSUE = "scm:inventory:unit:issue";

    /** 分拣扫码绑定。 */
    public static final String UNIT_BIND = "scm:inventory:unit:bind";

    /** 包装码作废。 */
    public static final String UNIT_VOID = "scm:inventory:unit:void";

    /** 更正申请（解绑必须经他人审批）。 */
    public static final String CORRECTION_APPLY = "scm:inventory:unit:correct:apply";

    /** 更正审批。 */
    public static final String CORRECTION_APPROVE = "scm:inventory:unit:correct:approve";

    /** 包装码称重补录：只对没有净重的码生效。 */
    public static final String UNIT_WEIGH = "scm:inventory:unit:weigh";

    /** 商品资质与批次检测报告的增删改；查看分别沿用商品查看权与包装码查看权。 */
    public static final String FILE_MANAGE = "scm:trace:file:manage";

    public static final String PHOTO_QUERY = "scm:trace:photo:query";
    public static final String PHOTO_UPLOAD = "scm:trace:photo:upload";
    public static final String PHOTO_REMOVE = "scm:trace:photo:remove";

    public static final String INSPECTION_MANAGE = "scm:trace:inspection:manage";
}
