package com.xsy.scm.base.module.support.redis;

import com.xsy.scm.base.common.util.XsyStringUtil;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.stereotype.Component;

/**
 *
 * redission对于password 为空处理有问题，重新设置下
 *
 */

@Component
public class RedissonPasswordConfigurationCustomizer implements RedissonAutoConfigurationCustomizer {
    @Override
    public void customize(Config configuration) {
        if (configuration.isSingleConfig() && XsyStringUtil.isEmpty(configuration.useSingleServer().getPassword())) {
            configuration.useSingleServer().setPassword(null);
        }

        if (configuration.isClusterConfig() && XsyStringUtil.isEmpty(configuration.useClusterServers().getPassword())) {
            configuration.useClusterServers().setPassword(null);
        }
        if (configuration.isSentinelConfig()
            && XsyStringUtil.isEmpty(configuration.useSentinelServers().getPassword())) {
            configuration.useSentinelServers().setPassword(null);
        }
    }
}
