package com.xsy.scm.balance.constant;

/**
 * 余额域权限码（稳定标识，前端 {@code v-privilege} 与后端 {@code @SaCheckPermission} 共用）。
 *
 * <p>
 * <b>充值权限到 3-12b 才落</b>：3-12a 时刻意没有预占 {@code scm:balance:recharge} ——
 * 当时充值入口形态未定，先占一个权限点会让「有这个权限」被误读成「已支持人工充值」。
 */
public final class ScmBalancePermission {

    /** 查询客户余额概览。 */
    public static final String QUERY = "scm:balance:query";

    /** 查询余额流水。 */
    public static final String MOVEMENT_QUERY = "scm:balance:movement:query";

    /** 人工更正余额（动客户的钱，独立权限）。 */
    public static final String CORRECTION = "scm:balance:correction";

    /** 发起在线充值（3-12b 真的开放了充值入口，此时才落这个权限）。 */
    public static final String RECHARGE = "scm:balance:recharge";

    public static final String REFUND = "scm:balance:refund";

    private ScmBalancePermission() {
    }
}
