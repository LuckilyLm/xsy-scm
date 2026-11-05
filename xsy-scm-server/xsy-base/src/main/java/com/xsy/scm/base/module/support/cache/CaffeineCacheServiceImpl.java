package com.xsy.scm.base.module.support.cache;

import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import com.xsy.scm.base.constant.ReloadConst;
import com.xsy.scm.base.module.support.reload.core.annoation.XsyReload;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * caffeine 缓存实现
 *
 */
public class CaffeineCacheServiceImpl implements CacheService {

    @Resource
    private CaffeineCacheManager caffeineCacheManager;

    /**
     * 获取所有缓存名称
     */
    @Override
    public List<String> cacheNames() {
        return Lists.newArrayList(caffeineCacheManager.getCacheNames());
    }

    /**
     * 某个缓存下的所有 key
     */
    @Override
    public List<String> cacheKey(String cacheName) {
        CaffeineCache cache = (CaffeineCache) caffeineCacheManager.getCache(cacheName);
        if (cache == null) {
            return Lists.newArrayList();
        }
        Set<Object> cacheKey = cache.getNativeCache().asMap().keySet();
        return cacheKey.stream().map(e -> e.toString()).collect(Collectors.toList());
    }

    /**
     * 移除某个 key
     */
    @Override
    public void removeCache(String cacheName) {
        CaffeineCache cache = (CaffeineCache) caffeineCacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    @XsyReload(ReloadConst.CACHE_SERVICE)
    public void clearAllCache() {
        Collection<String> cacheNames = caffeineCacheManager.getCacheNames();
        for (String name : cacheNames) {
            CaffeineCache cache = (CaffeineCache) caffeineCacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
