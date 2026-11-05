package com.xsy.scm.base.module.support.job.config;

import com.xsy.scm.base.module.support.job.core.XsyJob;
import com.xsy.scm.base.module.support.job.core.XsyJobLauncher;
import com.xsy.scm.base.module.support.job.repository.XsyJobRepository;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 定时任务 配置
 *
 */
@Configuration
@EnableConfigurationProperties(XsyJobConfig.class)
@ConditionalOnProperty(prefix = XsyJobConfig.CONFIG_PREFIX, name = "enabled", havingValue = "true")
public class XsyJobAutoConfiguration {

    private final XsyJobConfig jobConfig;

    private final XsyJobRepository jobRepository;

    private final List<XsyJob> jobInterfaceList;

    public XsyJobAutoConfiguration(XsyJobConfig jobConfig, XsyJobRepository jobRepository,
        List<XsyJob> jobInterfaceList) {
        this.jobConfig = jobConfig;
        this.jobRepository = jobRepository;
        this.jobInterfaceList = jobInterfaceList;
    }

    /**
     * 定时任务启动器
     *
     * @return
     */
    @Bean
    public XsyJobLauncher initJobLauncher(RedissonClient redissonClient) {
        return new XsyJobLauncher(jobConfig, jobRepository, jobInterfaceList, redissonClient);
    }
}
