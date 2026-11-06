package com.xsy.scm.base.module.support.redis;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.data.redis.cache.*;

import java.time.Duration;

import static com.xsy.scm.base.common.constant.StringConst.COLON;

/** 支持在 cacheName 中用 # 指定 TTL 的 RedisCacheManager，例如 user#10s；-1 表示永不过期。 */
@Slf4j
public class CustomRedisCacheManager extends RedisCacheManager {

    /**
     * 缓存全局前缀
     */
    private static final String CACHE_PREFIX = "cache";

    /**
     * 自定义 TTL 分隔符，用于在 cacheName 后附加过期时间
     */
    private static final String CUSTOM_TTL_SEPARATOR = "#";

    /**
     * 默认缓存过期时间：7 天
     */
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    public CustomRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration) {
        super(cacheWriter, defaultCacheConfiguration);
    }

    /**
     * 创建 RedisCache 实例，支持从 cacheName 解析 TTL
     *
     * @param name
     *            缓存名称（支持 name#ttl 格式）
     * @param cacheConfig
     *            默认缓存配置
     * @return RedisCache
     */
    @Override
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration cacheConfig) {
        Duration ttl = parseTtlFromCacheName(name);
        if (ttl == null) {
            ttl = DEFAULT_TTL;
        }

        CacheKeyPrefix keyPrefix = cacheName -> {
            if (StrUtil.isBlank(cacheName)) {
                return CACHE_PREFIX + COLON;
            }
            String[] parts = cacheName.split(CUSTOM_TTL_SEPARATOR, 2);
            String cleanName = StrUtil.trim(parts[0]);
            return CACHE_PREFIX + COLON + cleanName + COLON;
        };

        // 构建最终缓存配置：设置 key 前缀 + TTL
        RedisCacheConfiguration config = cacheConfig.computePrefixWith(keyPrefix).entryTtl(ttl);

        return super.createRedisCache(name, config);
    }

    /**
     * 从 cacheName 中解析 TTL
     *
     * @param name
     *            缓存名称，格式如：users#10m, products#2h, config#-1（永久）
     * @return 解析出的 Duration若无效则返回 null；若为 -1，则返回 Duration.ofMillis(-1) 表示永久缓存
     */
    private Duration parseTtlFromCacheName(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }

        String[] parts = name.split(CUSTOM_TTL_SEPARATOR, 2);
        if (parts.length < 2) {
            return null; // 无 TTL 部分
        }

        String ttlStr = StrUtil.trim(parts[1]);
        if (StrUtil.isBlank(ttlStr)) {
            return null;
        }

        // 特殊处理：-1 表示永久缓存
        if ("-1".equals(ttlStr)) {
            return Duration.ofMillis(-1); // Spring Redis 中负数 Duration 表示永不过期
        }

        try {
            Duration ttl = DurationStyle.detectAndParse(ttlStr);
            return ttl.getSeconds() > 0 ? ttl : null;
        } catch (IllegalArgumentException e) {
            log.error("解析缓存 TTL 失败，cacheName='{}', ttl='{}', 错误: {}", name, ttlStr, e);
            return null;
        }
    }
}
