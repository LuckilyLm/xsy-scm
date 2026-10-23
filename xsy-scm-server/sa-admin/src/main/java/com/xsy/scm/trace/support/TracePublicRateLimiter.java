package com.xsy.scm.trace.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.trace.constant.TraceErrorCode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * 公开扫码页的本地限流。
 *
 * <p>
 * 扫码页免登录，凭据只有标签上的令牌，所以限流是唯一的滥用防线：按 IP 挡批量枚举，按令牌挡针对单个码的高频抓取。
 *
 * <p>
 * 计数放在本机内存：当前是单实例部署，跨实例共享要换成 Redis。限流只是兜底，不是计费依据，多实例时放宽一倍可以接受。
 */
@Component
public class TracePublicRateLimiter {

    private static final int IP_LIMIT_PER_MINUTE = 30;

    private static final int TOKEN_LIMIT_PER_HOUR = 60;

    private final Cache<String, AtomicInteger> ipCounters = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1)).maximumSize(10_000).build();

    private final Cache<String, AtomicInteger> tokenCounters = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(1)).maximumSize(50_000).build();

    /** 任一维度超限即拒绝；IP 未知时只按令牌计。 */
    public void check(String clientIp, String publicToken) {
        if (clientIp != null && !clientIp.isBlank() && increment(ipCounters, clientIp) > IP_LIMIT_PER_MINUTE) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_PUBLIC_RATE_LIMITED);
        }
        if (increment(tokenCounters, publicToken) > TOKEN_LIMIT_PER_HOUR) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_PUBLIC_RATE_LIMITED);
        }
    }

    private static int increment(Cache<String, AtomicInteger> cache, String key) {
        AtomicInteger counter = cache.get(key, ignored -> new AtomicInteger());
        return counter.incrementAndGet();
    }
}
