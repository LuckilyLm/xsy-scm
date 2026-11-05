package com.xsy.scm.mall.decoration.domain.vo;

/** 客户端只拿已发布内容；草稿与版本信息不下发。 */
public record MallPublishedPageVO(String pageCode, String title, String content) {
}
