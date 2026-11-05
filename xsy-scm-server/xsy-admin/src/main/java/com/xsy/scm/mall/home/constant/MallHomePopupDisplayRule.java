package com.xsy.scm.mall.home.constant;

/** 弹窗展示频控：每日一次或每次进入都展示。频控在客户端按发布日期记录，避免每次开首页都请求一次记录接口。 */
public enum MallHomePopupDisplayRule {
    DAILY,
    EVERY_ENTRY
}
