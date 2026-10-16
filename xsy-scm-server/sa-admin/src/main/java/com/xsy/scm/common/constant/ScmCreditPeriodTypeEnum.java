package com.xsy.scm.common.constant;

/** 客户账期可以按金额阈值结算，或按时间周期结算，也可以不设置。两种模式互斥，由校验器和数据库共同保证。 */
public enum ScmCreditPeriodTypeEnum {

    BY_AMOUNT,

    BY_TIME
}
