package com.xsy.scm.finance.permission;

/**
 * 财务受保护端点的权限码（稳定标识）。
 */
public final class FinancePermission {

    public static final String RECEIVABLE_QUERY = "scm:finance:receivable:query";
    public static final String PAYABLE_QUERY = "scm:finance:payable:query";
    public static final String RECEIPT_QUERY = "scm:finance:receipt:query";
    public static final String PAYMENT_QUERY = "scm:finance:payment:query";
    public static final String WRITE_OFF_QUERY = "scm:finance:write-off:query";

    public static final String RECEIPT_ADD = "scm:finance:receipt:add";
    public static final String PAYMENT_ADD = "scm:finance:payment:add";
    public static final String WRITE_OFF_ADD = "scm:finance:write-off:add";
    public static final String PAYABLE_RED = "scm:finance:payable:red";

    /**
     * 抹零与反向抹零分开授权：会抹零不等于能撤销，撤销会把已放掉的余额变回欠款。
     */
    public static final String WAIVER_ADD = "scm:finance:waiver:add";
    public static final String WAIVER_REVERSE = "scm:finance:waiver:reverse";

    /**
     * 过账特殊单据：查询、登记、修改、作废各自授权。作废不复用修改权限 —— 作废会让一张对外账目失效，影响比改备注大。
     */
    public static final String POSTING_QUERY = "scm:finance:posting:query";
    public static final String POSTING_ADD = "scm:finance:posting:add";
    public static final String POSTING_UPDATE = "scm:finance:posting:update";
    public static final String POSTING_VOID = "scm:finance:posting:void";

    public static final String WRITE_OFF_REVERSE = "scm:finance:write-off:reverse";
    public static final String RECEIPT_REVERSE = "scm:finance:receipt:reverse";
    public static final String PAYMENT_REVERSE = "scm:finance:payment:reverse";
    public static final String EXPORT = "scm:finance:export";

    private FinancePermission() {
    }
}
