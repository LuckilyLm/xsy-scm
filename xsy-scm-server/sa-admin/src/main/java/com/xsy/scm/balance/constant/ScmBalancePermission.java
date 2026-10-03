package com.xsy.scm.balance.constant;

/**
 * 余额域权限码（稳定标识，前端 {@code v-privilege} 与后端 {@code @SaCheckPermission} 共用）。
 *
 * <p>
 * <b>刻意不预占充值权限</b>：3-12b 的充值入口要由支付渠道成功驱动，是否需要人工充值入口
 * 还没定。先占一个 {@code scm:balance:recharge} 会让「有这个权限点」被误读成「已经支持人工充值」。
 */
public final class ScmBalancePermission {

    /** 查询客户余额概览。 */
    public static final String QUERY = "scm:balance:query";

    /** 查询余额流水。 */
    public static final String MOVEMENT_QUERY = "scm:balance:movement:query";

    /** 人工更正余额（动客户的钱，独立权限）。 */
    public static final String CORRECTION = "scm:balance:correction";

    private ScmBalancePermission() {
    }
}
