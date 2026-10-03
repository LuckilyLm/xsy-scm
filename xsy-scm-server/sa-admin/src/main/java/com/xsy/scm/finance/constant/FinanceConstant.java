package com.xsy.scm.finance.constant;

import com.xsy.scm.finance.permission.FinancePermission;

/**
 * 财务域的固定口径：单号前缀与锁序 rank。
 *
 * <p>
 * <b>金额与数量一律 {@code NUMERIC(18,4)}、scale 4、{@code HALF_UP}</b>； 序列化沿用 {@code ScmFixedScale4Serializer}（null 写 null，绝不写
 * 0）。 展示可以按 2 位格式化，但存储与对账口径恒为 4 位。
 *
 * <p>
 * <b>财务域没有状态机</b>：这里刻意没有任何状态常量集合 —— 结清 / 部分核销 / 待核销都是读时派生视图，落库就会多出一个会漂移的副本。
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
     * {@code ScmIdempotencyService} 会自动按「登录身份: scope」再加一层前缀， 因此这里只写动作名，不要重复拼员工 id。
     */
    public static final String RECEIPT_ADD_SCOPE = "FINANCE_RECEIPT_ADD";

    /**
     * 付款登记的幂等 scope（与 {@code Idempotency-Key} 一起定位一条命令）。
     *
     * <p>
     * 它防的是<b>重复请求</b>；退款付款还多一层<b>重复事实</b>防护 （{@code uk_finance_payment_source_active}），所以两个不同幂等键同时付同一张退款
     * 也只会在库里留下一笔付款。供应商付款没有来源列，只有前者这一层。
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

    /** 核销新增命令幂等 scope；实际 scope 还要拼资金来源类型与 id。 */
    public static final String WRITE_OFF_ADD_SCOPE = "FINANCE_WRITE_OFF_ADD";

    /** 核销反向命令幂等 scope；实际 scope 还要拼原核销 id。 */
    public static final String WRITE_OFF_REVERSE_SCOPE = "FINANCE_WRITE_OFF_REVERSE";

    /** 手工红字应付命令幂等 scope；实际 scope 还要拼原应付 id。 */
    public static final String PAYABLE_RED_SCOPE = "FINANCE_PAYABLE_RED";

    /**
     * 四个破坏性动作各一条独立权限：能登记一笔款的人不必然是能冲掉一笔款的人， 因此它们既不与 {@code *:add} 合并，也不合成一个 {@code scm:finance:reverse}。
     */
    public static final String WRITE_OFF_REVERSE_PERM = FinancePermission.WRITE_OFF_REVERSE;
    public static final String RECEIPT_REVERSE_PERM = FinancePermission.RECEIPT_REVERSE;
    public static final String PAYMENT_REVERSE_PERM = FinancePermission.PAYMENT_REVERSE;

    /**
     * 导出许可，<b>绝不扩大查询范围</b>：导出端点要求 「对应 {@code *:query} AND 本权限」，且与列表共用同一次范围解析结果。
     */
    public static final String EXPORT_PERM = FinancePermission.EXPORT;

    /**
     * 写命令的加锁层级：单据锁先于余额锁，余额锁最后。
     *
     * <p>
     * 所有用户命令按 rank 升序、同 rank 按 id 升序 {@code SELECT … FOR UPDATE}。 收付款反向与核销**共用同一把原收付款行锁**（rank 1 / 2），因此两者天然串行 ——
     * 这是并发安全校验「反向前已用额必须 = 0」的前提， 「先查再判断」的乐观写法在这里不成立。
     *
     * <p>
     * 财务域**不获取**任何 {@code inventory_balance} 行锁（全局不变量 4）。
     */
    public static final int LOCK_RANK_RECEIPT = 1;
    public static final int LOCK_RANK_PAYMENT = 2;
    public static final int LOCK_RANK_RECEIVABLE = 3;
    public static final int LOCK_RANK_PAYABLE = 4;
    public static final int LOCK_RANK_WRITE_OFF = 5;

    /**
     * 财务事实的统一精度：scale 4。
     */
    public static final int AMOUNT_SCALE = 4;

    private FinanceConstant() {
    }
}
