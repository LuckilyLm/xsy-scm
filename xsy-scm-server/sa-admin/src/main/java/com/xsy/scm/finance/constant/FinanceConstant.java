package com.xsy.scm.finance.constant;

import com.xsy.scm.finance.permission.FinancePermission;

/**
 * 财务金额与数量统一保留四位小数，按 HALF_UP 处理。应收应付没有独立结清状态，结清和未核销额都从财务记录计算。
 */
public final class FinanceConstant {

    public static final String ORDER_FUNDING_OPERATOR = "SYSTEM:ORDER_FUNDING";

    /**
     * 应收单号前缀：AR + 业务日(Asia/Shanghai) + 全局非重置序号，拼接走 {@code ScmDocumentNumbers.format}。
     */
    public static final String RECEIVABLE_NO_PREFIX = "AR";

    /**
     * 应付单号前缀。
     */
    public static final String PAYABLE_NO_PREFIX = "AP";

    /**
     * 收款单号前缀。
     */
    public static final String RECEIPT_NO_PREFIX = "RC";

    /**
     * 付款单号前缀。
     */
    public static final String PAYMENT_NO_PREFIX = "PM";

    /**
     * 核销单号前缀。
     */
    public static final String WRITE_OFF_NO_PREFIX = "WO";

    /**
     * 抹零单号前缀。
     */
    public static final String WAIVER_NO_PREFIX = "WV";

    public static final String RECEIVABLE_QUERY_PERM = FinancePermission.RECEIVABLE_QUERY;
    public static final String PAYABLE_QUERY_PERM = FinancePermission.PAYABLE_QUERY;
    public static final String RECEIPT_QUERY_PERM = FinancePermission.RECEIPT_QUERY;
    public static final String PAYMENT_QUERY_PERM = FinancePermission.PAYMENT_QUERY;
    public static final String WRITE_OFF_QUERY_PERM = FinancePermission.WRITE_OFF_QUERY;

    public static final String RECEIPT_ADD_PERM = FinancePermission.RECEIPT_ADD;
    public static final String PAYMENT_ADD_PERM = FinancePermission.PAYMENT_ADD;
    public static final String WRITE_OFF_ADD_PERM = FinancePermission.WRITE_OFF_ADD;
    public static final String PAYABLE_RED_PERM = FinancePermission.PAYABLE_RED;

    /**
     * 收款登记的幂等 scope（与 {@code Idempotency-Key} 一起定位一条命令）。
     *
     * <p>
     * {@code ScmIdempotencyService} 会自动按「登录身份: scope」再加一层前缀，因此这里只写动作名，不要重复拼员工 id。
     */
    public static final String RECEIPT_ADD_SCOPE = "FINANCE_RECEIPT_ADD";

    /**
     * 货到付款收款的幂等 scope。
     *
     * <p>
     * 它只防重复请求；重复数据另由 {@code uk_finance_receipt_source_active} 按（来源类型, 订单 id）唯一约束兜底： 两个不同幂等键同时收同一张 COD 订单，也只会留下一笔收款。
     */
    public static final String COD_COLLECTION_SCOPE = "FINANCE_COD_COLLECTION";

    /**
     * 幂等键防重复请求；退款付款还靠退款单来源唯一约束防重复数据，两者都不能省。
     */
    public static final String PAYMENT_ADD_SCOPE = "FINANCE_PAYMENT_ADD";

    /**
     * 收款反向命令幂等 scope；实际 scope 还要拼原收款 id，避免跨单据复用同一 key。
     */
    public static final String RECEIPT_REVERSE_SCOPE = "FINANCE_RECEIPT_REVERSE";

    /**
     * 付款反向命令幂等 scope；实际 scope 还要拼原付款 id，避免跨单据复用同一 key。
     */
    public static final String PAYMENT_REVERSE_SCOPE = "FINANCE_PAYMENT_REVERSE";

    /**
     * 核销新增命令幂等 scope；实际 scope 还要拼资金来源类型与 id。
     */
    public static final String WRITE_OFF_ADD_SCOPE = "FINANCE_WRITE_OFF_ADD";

    /**
     * 核销反向命令幂等 scope；实际 scope 还要拼原核销 id。
     */
    public static final String WRITE_OFF_REVERSE_SCOPE = "FINANCE_WRITE_OFF_REVERSE";

    /**
     * 手工红字应付命令幂等 scope；实际 scope 还要拼原应付 id。
     */
    public static final String PAYABLE_RED_SCOPE = "FINANCE_PAYABLE_RED";

    /**
     * 抹零新增 / 反向的幂等 scope；反向还要拼原抹零 id。
     */
    public static final String WAIVER_ADD_SCOPE = "FINANCE_WAIVER_ADD";
    public static final String WAIVER_REVERSE_SCOPE = "FINANCE_WAIVER_REVERSE";

    /**
     * 自动 FIFO 核销：同一收款 / 付款只允许一段连续自动分配，幂等 scope 拼来源类型与 id。
     */
    public static final String WRITE_OFF_AUTO_SCOPE = "FINANCE_WRITE_OFF_AUTO";

    /**
     * 四个反向/冲减动作各用一条独立权限：能登记一笔款的人不必然能冲掉一笔款，因此不与 {@code *:add} 合并，也不合成一个 {@code scm:finance:reverse}。
     */
    public static final String WRITE_OFF_REVERSE_PERM = FinancePermission.WRITE_OFF_REVERSE;
    public static final String RECEIPT_REVERSE_PERM = FinancePermission.RECEIPT_REVERSE;
    public static final String PAYMENT_REVERSE_PERM = FinancePermission.PAYMENT_REVERSE;

    /**
     * 导出许可，不扩大查询范围：导出端点要求「对应的 {@code *:query} AND 本权限」，并与列表共用同一次范围解析结果。
     */
    public static final String EXPORT_PERM = FinancePermission.EXPORT;

    /**
     * 财务写入先锁单据再锁收付款，余额锁最后获取；同级锁按 ID 升序。撤销与核销要持有同一原收付款行锁，避免并发重复使用金额。
     */
    public static final int LOCK_RANK_RECEIPT = 1;
    public static final int LOCK_RANK_PAYMENT = 2;
    public static final int LOCK_RANK_RECEIVABLE = 3;
    public static final int LOCK_RANK_PAYABLE = 4;
    public static final int LOCK_RANK_WRITE_OFF = 5;

    /**
     * 财务金额的统一精度：4 位小数。
     */
    public static final int AMOUNT_SCALE = 4;

    private FinanceConstant() {
    }
}
