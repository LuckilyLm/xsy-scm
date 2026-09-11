package com.xianshuyuan.scm.marketing.entity;

/**
 * 促销活动状态。过期由服务端在查询时按结束时间推导，不落库改写。
 */
public enum PromotionStatus {
    DRAFT,
    ENABLED,
    DISABLED,
    EXPIRED
}
