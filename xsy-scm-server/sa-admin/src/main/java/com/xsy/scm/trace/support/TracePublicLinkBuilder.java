package com.xsy.scm.trace.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 包装码标签上的扫码链接。
 *
 * <p>
 * 链接规则只在这里定义一次：后台标签打印与秤端打标都取同一个值，两处各拼一次模板迟早会分叉。
 *
 * <p>
 * 没配置 {@code scm.trace.public-base-url} 时返回空串：标签只印码不印二维码，而不是印一个打不开的地址。
 */
@Component
public class TracePublicLinkBuilder {

    private final String baseUrl;

    public TracePublicLinkBuilder(@Value("${scm.trace.public-base-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : stripTrailingSlash(baseUrl.trim());
    }

    /** 返回可直接编码进二维码的绝对地址；未配置时为空串。 */
    public String build(String publicToken) {
        if (baseUrl.isEmpty() || publicToken == null || publicToken.isBlank()) {
            return "";
        }
        // 后台是哈希路由：地址里的 # 不能省，否则扫码落到服务器上的静态路径会 404
        return baseUrl + "/#/t/" + publicToken.trim();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
