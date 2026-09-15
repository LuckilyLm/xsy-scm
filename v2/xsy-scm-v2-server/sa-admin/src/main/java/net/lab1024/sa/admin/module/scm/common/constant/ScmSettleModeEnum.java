package net.lab1024.sa.admin.module.scm.common.constant;

/**
 * 客户结算方式。
 *
 * <p>{@code INDEPENDENT} = 独立结算（客户自己作为结算主体）；
 * {@code GROUP} = 集团结算（下属单位下单、由上级集团统一结算）。
 *
 * <p>W2 只落库该字段，<b>不实现</b>结算逻辑；集团结算与订单归属不是同一概念，W3 引入订单域时
 * 不得把二者合并。
 */
public enum ScmSettleModeEnum {
    INDEPENDENT,
    GROUP
}
