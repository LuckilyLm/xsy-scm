package com.xsy.scm.payment.constant;

/**
 * 支付域权限码（稳定标识，前端 {@code v-privilege} 与后端 {@code @SaCheckPermission} 共用）。
 *
 * <p>
 * <b>刻意不为 Mock 渠道单造一套权限模型</b>：模拟入口是「当前开发 provider 的受控操作」，
 * 它复用支付域已有的权限码即可。给 Mock 单独一套生产权限，会让人误以为它是一条正式业务通道。
 */
public final class ScmPaymentPermission {

    /** 创建支付意图（向渠道发起支付）。 */
    public static final String INTENT_CREATE = "scm:payment:intent:create";

    /** 查询支付交易。 */
    public static final String TRANSACTION_QUERY = "scm:payment:transaction:query";

    /** 发起退款。 */
    public static final String REFUND_CREATE = "scm:payment:refund:create";

    /** 查询退款。 */
    public static final String REFUND_QUERY = "scm:payment:refund:query";

    /** 查询回调记录。 */
    public static final String CALLBACK_QUERY = "scm:payment:callback:query";

    /** 执行对账。 */
    public static final String RECONCILIATION_RUN = "scm:payment:reconciliation:run";

    /** 查询对账结果。 */
    public static final String RECONCILIATION_QUERY = "scm:payment:reconciliation:query";

    private ScmPaymentPermission() {
    }
}
