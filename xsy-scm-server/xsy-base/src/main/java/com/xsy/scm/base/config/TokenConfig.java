package com.xsy.scm.base.config;

import cn.dev33.satoken.config.SaTokenConfig;
import jakarta.annotation.Resource;
import com.xsy.scm.base.module.support.securityprotect.service.Level3ProtectConfigService;
import org.springframework.context.annotation.Configuration;

/**
 *
 * 三级等保配置初始化后最低活跃频率全局配置
 *
 */

@Configuration
public class TokenConfig {

    @Resource
    private Level3ProtectConfigService level3ProtectConfigService;

    // 此配置会覆盖 xsy-base.yaml 中的配置
    @Resource
    public void configSaToken(SaTokenConfig config) {

        config.setActiveTimeout(level3ProtectConfigService.getLoginActiveTimeoutSeconds());
    }

}
