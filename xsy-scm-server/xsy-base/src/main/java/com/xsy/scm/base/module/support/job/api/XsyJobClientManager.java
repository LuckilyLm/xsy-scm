package com.xsy.scm.base.module.support.job.api;

import cn.hutool.core.util.IdUtil;
import com.google.common.collect.Lists;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import com.xsy.scm.base.module.support.job.api.domain.XsyJobMsg;
import com.xsy.scm.base.module.support.job.config.XsyJobAutoConfiguration;
import com.xsy.scm.base.module.support.job.core.XsyJob;
import com.xsy.scm.base.module.support.job.core.XsyJobExecutor;
import com.xsy.scm.base.module.support.job.core.XsyJobLauncher;
import com.xsy.scm.base.module.support.job.repository.XsyJobRepository;
import com.xsy.scm.base.module.support.job.repository.domain.XsyJobEntity;
import org.redisson.api.RLock;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务执行端管理 分布式系统之间 用发布/订阅消息的形式 来管理多个job
 *
 */
@ConditionalOnBean(XsyJobAutoConfiguration.class)
@Slf4j
@Service
public class XsyJobClientManager {

    private final XsyJobLauncher jobLauncher;

    private final XsyJobRepository jobRepository;

    private final List<XsyJob> jobInterfaceList;

    private static final String EXECUTE_LOCK = "xsy-job-lock-msg-execute-";

    private static final String TOPIC = "xsy-job-instance";

    private final RedissonClient redissonClient;

    private final RTopic topic;

    private final XsyJobMsgListener jobMsgListener;

    public XsyJobClientManager(XsyJobLauncher jobLauncher, XsyJobRepository jobRepository,
        List<XsyJob> jobInterfaceList, RedissonClient redissonClient) {
        this.jobLauncher = jobLauncher;
        this.jobRepository = jobRepository;
        this.jobInterfaceList = jobInterfaceList;
        this.redissonClient = redissonClient;

        // 添加监听器
        this.topic = redissonClient.getTopic(TOPIC);
        this.jobMsgListener = new XsyJobMsgListener();
        topic.addListener(XsyJobMsg.class, jobMsgListener);
        log.info("==== XsyJob ==== client-manager init");
    }

    /**
     * 发布消息
     */
    public void publishToClient(XsyJobMsg msgDTO) {
        msgDTO.setMsgId(IdUtil.fastSimpleUUID());
        topic.publish(msgDTO);
    }

    /**
     * 处理消息
     */
    private class XsyJobMsgListener implements MessageListener<XsyJobMsg> {

        @Override
        public void onMessage(CharSequence channel, XsyJobMsg msg) {
            log.info("==== XsyJob ==== on-message :{}", msg);
            // 判断消息类型 业务简单就直接判断 复杂的话可以策略模式
            XsyJobMsg.MsgTypeEnum msgType = msg.getMsgType();
            // 更新任务
            if (XsyJobMsg.MsgTypeEnum.UPDATE_JOB == msgType) {
                updateJob(msg.getJobId());
            }
            // 执行任务
            if (XsyJobMsg.MsgTypeEnum.EXECUTE_JOB == msgType) {
                executeJob(msg);
            }
        }
    }

    /**
     * 获取任务执行类
     *
     * @param jobClass
     * @return
     */
    private Optional<XsyJob> queryJobImpl(String jobClass) {
        return jobInterfaceList.stream().filter(e -> Objects.equals(e.getClassName(), jobClass)).findFirst();
    }

    /**
     * 更新任务
     *
     * @param jobId
     */
    private void updateJob(Integer jobId) {
        XsyJobEntity jobEntity = jobRepository.getJobDao().selectById(jobId);
        if (null == jobEntity) {
            return;
        }
        jobLauncher.startOrRefreshJob(Lists.newArrayList(jobEntity));
    }

    /**
     * 立即执行任务
     *
     * @param msg
     */
    private void executeJob(XsyJobMsg msg) {
        Integer jobId = msg.getJobId();
        XsyJobEntity jobEntity = jobRepository.getJobDao().selectById(jobId);
        if (null == jobEntity) {
            return;
        }
        // 获取定时任务实现类
        Optional<XsyJob> optional = this.queryJobImpl(jobEntity.getJobClass());
        if (!optional.isPresent()) {
            return;
        }

        // 获取执行锁 无需主动释放
        RLock rLock = redissonClient.getLock(EXECUTE_LOCK + msg.getMsgId());
        try {
            boolean getLock = rLock.tryLock(0, 20, TimeUnit.SECONDS);
            if (!getLock) {
                return;
            }
        } catch (InterruptedException e) {
            log.error("==== XsyJob ==== msg execute err:", e);
            return;
        }

        // 通过执行器 执行任务
        jobEntity.setParam(msg.getParam());
        XsyJobExecutor jobExecutor = new XsyJobExecutor(jobEntity, jobRepository, optional.get(), redissonClient);
        jobExecutor.execute(msg.getUpdateName());
    }

    @PreDestroy
    public void destroy() {
        topic.removeListener(jobMsgListener);
    }

}
