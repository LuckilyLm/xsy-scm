package com.xsy.scm.base.module.support.job.core;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import com.xsy.scm.base.module.support.job.config.XsyJobConfig;
import com.xsy.scm.base.module.support.job.constant.XsyJobConst;
import com.xsy.scm.base.module.support.job.constant.XsyJobUtil;
import com.xsy.scm.base.module.support.job.repository.XsyJobRepository;
import com.xsy.scm.base.module.support.job.repository.domain.XsyJobEntity;
import org.redisson.api.RedissonClient;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 定时任务 作业启动类
 *
 */
@Slf4j
public class XsyJobLauncher {

    private final XsyJobRepository jobRepository;

    private final List<XsyJob> jobInterfaceList;

    private final RedissonClient redissonClient;

    public XsyJobLauncher(XsyJobConfig jobConfig, XsyJobRepository jobRepository, List<XsyJob> jobInterfaceList,
        RedissonClient redissonClient) {
        this.jobRepository = jobRepository;
        this.jobInterfaceList = jobInterfaceList;
        this.redissonClient = redissonClient;

        // init job scheduler
        XsyJobScheduler.init(jobConfig);

        // 任务自动检测配置 固定1个线程
        Integer initDelay = jobConfig.getInitDelay();
        Boolean refreshEnabled = jobConfig.getDbRefreshEnabled();
        Integer refreshInterval = jobConfig.getDbRefreshInterval();

        ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("XsyJobLauncher-%d").build();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, factory);
        Runnable launcherRunnable = () -> {
            try {
                // 查询所有任务
                List<XsyJobEntity> xsyJobList = this.queryJob();
                this.startOrRefreshJob(xsyJobList);
            } catch (Throwable t) {
                log.error("XsyJob Error:", t);
            }
            // 只在启动时 执行一次
            if (!refreshEnabled) {
                executor.shutdown();
            }
        };
        executor.scheduleWithFixedDelay(launcherRunnable, initDelay, refreshInterval, TimeUnit.SECONDS);

        // 打印信息
        String refreshDesc = refreshEnabled ? "开启|检测间隔" + refreshInterval + "秒" : "关闭";
        String format = String.format(XsyJobConst.LOGO, jobConfig.getCorePoolSize(), initDelay, refreshDesc);
        XsyJobUtil.printInfo(format);
    }

    /**
     * 查询数据库 启动/刷新任务
     */
    public void startOrRefreshJob(List<XsyJobEntity> xsyJobList) {
        // 查询任务配置
        if (CollectionUtils.isEmpty(xsyJobList) || CollectionUtils.isEmpty(jobInterfaceList)) {
            log.info("==== XsyJob ==== job list empty");
            return;
        }

        // 任务实现类
        Map<String, XsyJob> jobImplMap = jobInterfaceList.stream()
            .collect(Collectors.toMap(XsyJob::getClassName, Function.identity()));
        for (XsyJobEntity jobEntity : xsyJobList) {
            // 任务是否存在 判断是否需要更新
            Integer jobId = jobEntity.getJobId();
            XsyJobEntity oldJobEntity = XsyJobScheduler.getJobInfo(jobId);
            if (null != oldJobEntity) {
                // 不需要更新
                if (!isNeedUpdate(oldJobEntity, jobEntity)) {
                    continue;
                }
                // 需要更新 移除原任务
                XsyJobScheduler.removeJob(jobId);
            }
            // 任务未开启
            if (!jobEntity.getEnabledFlag()) {
                continue;
            }
            // 任务删除
            if (jobEntity.getDeletedFlag()) {
                continue;
            }
            // 查找任务实现类
            XsyJob jobImpl = jobImplMap.get(jobEntity.getJobClass());
            if (null == jobImpl) {
                continue;
            }
            // 添加任务
            XsyJobExecutor jobExecute = new XsyJobExecutor(jobEntity, jobRepository, jobImpl, redissonClient);
            XsyJobScheduler.addJob(jobExecute);
        }
        List<XsyJobEntity> runjJobList = XsyJobScheduler.getJobInfo();
        List<String> jobNameList = runjJobList.stream().map(XsyJobEntity::getJobName).collect(Collectors.toList());
        log.info("==== XsyJob ==== start/refresh job num:{}->{}", runjJobList.size(), jobNameList);
    }

    /**
     * 查询全部任务
     *
     * @return
     */
    private List<XsyJobEntity> queryJob() {
        return jobRepository.getJobDao().selectList(null);
    }

    /**
     * 手动判断 任务配置 是否需要更新 新增字段的话 在这个方法里增加判断
     *
     * @return
     */
    private static boolean isNeedUpdate(XsyJobEntity oldJob, XsyJobEntity newJob) {
        // cron为空时 fixedDelay 才有意义
        return !Objects.equals(oldJob.getEnabledFlag(), newJob.getEnabledFlag())
            || !Objects.equals(oldJob.getDeletedFlag(), newJob.getDeletedFlag())
            || !Objects.equals(oldJob.getTriggerType(), newJob.getTriggerType())
            || !Objects.equals(oldJob.getTriggerValue(), newJob.getTriggerValue())
            || !Objects.equals(oldJob.getJobClass(), newJob.getJobClass());
    }

    @PreDestroy
    public void destroy() {
        XsyJobScheduler.destroy();
        log.info("==== XsyJob ==== destroy job");
    }
}
