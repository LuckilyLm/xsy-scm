package com.xsy.scm.balance.constant;

/**
 * 余额域权限码，前端 {@code v-privilege} 与后端 {@code @SaCheckPermission} 共用。
 *
 * <p>
 * 只有实际操作存在时才登记权限码，避免「拥有该权限」被误读成「该操作已可用」。
 */
public final class ScmBalancePermission {

    /** 查询客户余额概览。 */
    public static final String QUERY = "scm:balance:query";

    /** 查询余额流水。 */
    public static final String MOVEMENT_QUERY = "scm:balance:movement:query";

    /** 人工更正余额；会动客户的钱，独立权限。 */
    public static final String CORRECTION = "scm:balance:correction";

    /** 发起在线充值。 */
    public static final String RECHARGE = "scm:balance:recharge";

    public static final String REFUND = "scm:balance:refund";

    private ScmBalancePermission() {
    }
}
