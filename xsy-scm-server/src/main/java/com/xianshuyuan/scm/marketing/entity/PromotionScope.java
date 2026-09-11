package com.xianshuyuan.scm.marketing.entity;

/**
 * 促销适用范围。SKU / CATEGORY / CUSTOMER 时通过 scope_ids 指定具体对象，ALL 表示全站。
 */
public enum PromotionScope {
    ALL,
    CUSTOMER,
    CATEGORY,
    SKU
}
