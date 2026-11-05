package com.xsy.scm.admin.config;

import com.xsy.scm.base.module.support.operatelog.core.OperateLogAspect;
import com.xsy.scm.base.module.support.operatelog.core.OperateLogConfig;
import org.springframework.context.annotation.Configuration;

/**
 * 操作日志切面 配置
 *
 */
@Configuration
public class OperateLogAspectConfig extends OperateLogAspect {

    /**
     * 配置信息
     */
    @Override
    public OperateLogConfig getOperateLogConfig() {
        return OperateLogConfig.builder().corePoolSize(1).queueCapacity(10000).build();
    }

}
