package com.xianshuyuan.scm.marketing.entity;

/**
 * 促销活动类型。与 V30 迁移中 marketing_promotion.type 的检查约束保持一致。
 */
public enum PromotionType {
    FLASH_SALE,
    FULL_REDUCE,
    FULL_GIFT,
    TIME_LIMIT
}
