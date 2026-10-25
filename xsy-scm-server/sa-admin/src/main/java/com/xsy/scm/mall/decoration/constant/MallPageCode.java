package com.xsy.scm.mall.decoration.constant;

import java.util.Arrays;
import java.util.Optional;

/** 可维护的商城单页；页面编码稳定，客户端按编码取发布内容。 */
public enum MallPageCode {
    ANNOUNCEMENT("商城公告"),
    TERMS("服务条款"),
    ABOUT("关于我们"),
    AFTER_SALE("售后规则");

    private final String title;

    MallPageCode(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public static Optional<MallPageCode> of(String code) {
        return Arrays.stream(values()).filter(page -> page.name().equals(code)).findFirst();
    }
}
