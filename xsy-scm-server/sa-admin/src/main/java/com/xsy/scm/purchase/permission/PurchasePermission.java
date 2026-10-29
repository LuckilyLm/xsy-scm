package com.xsy.scm.purchase.permission;

import com.xsy.scm.common.permission.ScmCrossDomainPermission;

/**
 * 采购接口对外发布的权限码（稳定标识）。
 */
public final class PurchasePermission {
    public static final String FINANCE_CONFIRM = "scm:purchase:finance-confirm";
    public static final String COST_ADJUSTMENT = "scm:purchase:cost-adjustment";

    public static final String DEMAND_QUERY = "scm:purchase:demand:query";

    public static final String DEMAND_GENERATE = "scm:purchase:demand:generate";

    public static final String DEMAND_ALLOCATE = "scm:purchase:demand:allocate";

    public static final String DEMAND_BATCH_CREATE = "scm:purchase:demand:batch:create";

    public static final String DEMAND_BATCH_GENERATE = "scm:purchase:demand:batch:generate";

    public static final String DEMAND_BATCH_QUERY = "scm:purchase:demand:batch:query";

    public static final String INQUIRY_QUERY = "scm:purchase:inquiry:query";
    public static final String INQUIRY_CREATE = "scm:purchase:inquiry:create";
    public static final String INQUIRY_QUOTE = "scm:purchase:inquiry:quote";
    public static final String INQUIRY_ACCEPT = "scm:purchase:inquiry:accept";

    public static final String QUERY = "scm:purchase:query";

    public static final String LOG_QUERY = "scm:purchase:log:query";

    public static final String ADD = "scm:purchase:add";

    public static final String UPDATE = "scm:purchase:update";

    public static final String ASSIGN = ScmCrossDomainPermission.PURCHASE_ASSIGN;

    public static final String SUBMIT = "scm:purchase:submit";

    public static final String CANCEL = "scm:purchase:cancel";

    public static final String SHORT_CLOSE = "scm:purchase:short-close";

    /**
     * 生成采购单分享链接（二维码）。
     */
    public static final String SHARE_CREATE = "scm:purchase:share:create";

    /**
     * 撤销采购单分享链接。
     */
    public static final String SHARE_REVOKE = "scm:purchase:share:revoke";

    public static final String DELETE = "scm:purchase:delete";

    /** 补价：把待定价行补成有价（窗口内、未锁单）。 */
    public static final String PRICE_FILL = "scm:purchase:price:fill";

    /** 改价：已定价行在收货确认前调整价格，需要独立权限与原因。 */
    public static final String PRICE_ADJUST = "scm:purchase:price:adjust";

    /** 锁单 / 解锁：对账完成后关掉补价入口；解锁必须说明原因。 */
    public static final String LOCK = "scm:purchase:lock";

    public static final String UNLOCK = "scm:purchase:unlock";

    public static final String RECEIPT_QUERY = "scm:purchase:receipt:query";

    public static final String RECEIPT_ADD = "scm:purchase:receipt:add";

    public static final String RECEIPT_UPDATE = "scm:purchase:receipt:update";

    public static final String RECEIPT_CONFIRM = "scm:purchase:receipt:confirm";

    public static final String RECEIPT_PUTAWAY = "scm:purchase:receipt:putaway";

    public static final String RECEIPT_DELETE = "scm:purchase:receipt:delete";

    public static final String SCOPE_ALL_QUERY = ScmCrossDomainPermission.PURCHASE_SCOPE_ALL_QUERY;

    private PurchasePermission() {
    }
}
