package com.xsy.scm.base.module.support.job.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import com.xsy.scm.base.common.code.UserErrorCode;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.XsyBeanUtil;
import com.xsy.scm.base.common.util.XsyPageUtil;
import com.xsy.scm.base.module.support.job.api.domain.*;
import com.xsy.scm.base.module.support.job.config.XsyJobAutoConfiguration;
import com.xsy.scm.base.module.support.job.constant.XsyJobTriggerTypeEnum;
import com.xsy.scm.base.module.support.job.constant.XsyJobUtil;
import com.xsy.scm.base.module.support.job.repository.XsyJobDao;
import com.xsy.scm.base.module.support.job.repository.XsyJobLogDao;
import com.xsy.scm.base.module.support.job.repository.domain.XsyJobEntity;
import com.xsy.scm.base.module.support.job.repository.domain.XsyJobLogEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 定时任务 接口业务管理 如果不需要通过接口管理定时任务 可以删除此类
 *
 */
@ConditionalOnBean(XsyJobAutoConfiguration.class)
@Service
public class XsyJobService {

    @Resource
    private XsyJobDao jobDao;

    @Resource
    private XsyJobLogDao jobLogDao;

    @Resource
    private XsyJobClientManager jobClientManager;

    /**
     * 查询 定时任务详情
     *
     * @param jobId
     * @return
     */
    public ResponseDTO<XsyJobVO> queryJobInfo(Integer jobId) {
        XsyJobEntity jobEntity = jobDao.selectById(jobId);
        if (null == jobEntity) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        XsyJobVO jobVO = XsyBeanUtil.copy(jobEntity, XsyJobVO.class);
        // 处理设置job详情
        this.handleJobInfo(Lists.newArrayList(jobVO));
        return ResponseDTO.ok(jobVO);
    }

    /**
     * 分页查询 定时任务
     *
     * @param queryForm
     * @return
     */
    public ResponseDTO<PageResult<XsyJobVO>> queryJob(XsyJobQueryForm queryForm) {
        Page<?> page = XsyPageUtil.convert2PageQuery(queryForm);
        List<XsyJobVO> jobList = jobDao.query(page, queryForm);
        PageResult<XsyJobVO> pageResult = XsyPageUtil.convert2PageResult(page, jobList);
        // 处理设置job详情
        this.handleJobInfo(jobList);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * 处理设置 任务信息
     *
     * @param jobList
     */
    private void handleJobInfo(List<XsyJobVO> jobList) {
        if (CollectionUtils.isEmpty(jobList)) {
            return;
        }
        // 查询最后一次执行记录
        List<Long> logIdList = jobList.stream().map(XsyJobVO::getLastExecuteLogId).filter(Objects::nonNull)
            .collect(Collectors.toList());
        Map<Long, XsyJobLogVO> lastLogMap = Collections.emptyMap();
        if (CollectionUtils.isNotEmpty(logIdList)) {
            lastLogMap = jobLogDao.selectBatchIds(logIdList).stream()
                .collect(Collectors.toMap(XsyJobLogEntity::getLogId, e -> XsyBeanUtil.copy(e, XsyJobLogVO.class)));
        }

        // 循环处理任务信息
        for (XsyJobVO jobVO : jobList) {
            // 设置最后一次执行记录
            Long lastExecuteLogId = jobVO.getLastExecuteLogId();
            if (null != lastExecuteLogId) {
                jobVO.setLastJobLog(lastLogMap.get(lastExecuteLogId));
            }
            // 计算未来5次执行时间
            if (jobVO.getEnabledFlag()) {
                List<LocalDateTime> nextTimeList = XsyJobUtil.queryNextTimeFromNow(jobVO.getTriggerType(),
                    jobVO.getTriggerValue(), jobVO.getLastExecuteTime(), 5);
                jobVO.setNextJobExecuteTimeList(nextTimeList);
            }
        }
    }

    /**
     * 分页查询 定时任务-执行记录
     *
     * @param queryForm
     * @return
     */
    public ResponseDTO<PageResult<XsyJobLogVO>> queryJobLog(XsyJobLogQueryForm queryForm) {
        Page<?> page = XsyPageUtil.convert2PageQuery(queryForm);
        List<XsyJobLogVO> jobList = jobLogDao.query(page, queryForm);
        PageResult<XsyJobLogVO> pageResult = XsyPageUtil.convert2PageResult(page, jobList);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * 添加定时任务
     *
     * @param addForm
     * @return
     */
    public synchronized ResponseDTO<String> addJob(XsyJobAddForm addForm) {
        // 校验参数
        ResponseDTO<String> checkRes = this.checkParam(addForm);
        if (!checkRes.getOk()) {
            return checkRes;
        }

        // 校验重复的执行类
        XsyJobEntity existJobClass = jobDao.selectByJobClass(addForm.getJobClass());
        if (null != existJobClass && !existJobClass.getDeletedFlag()) {
            return ResponseDTO.userErrorParam("已经存在相同的执行类");
        }

        // 添加数据
        XsyJobEntity jobEntity = XsyBeanUtil.copy(addForm, XsyJobEntity.class);
        jobDao.insert(jobEntity);

        // 更新执行端
        XsyJobMsg jobMsg = new XsyJobMsg();
        jobMsg.setJobId(jobEntity.getJobId());
        jobMsg.setMsgType(XsyJobMsg.MsgTypeEnum.UPDATE_JOB);
        jobMsg.setUpdateName(addForm.getUpdateName());
        jobClientManager.publishToClient(jobMsg);
        return ResponseDTO.ok();
    }

    /**
     * 更新定时任务
     *
     * @param updateForm
     * @return
     */
    public synchronized ResponseDTO<String> updateJob(XsyJobUpdateForm updateForm) {
        // 校验参数
        Integer jobId = updateForm.getJobId();
        XsyJobEntity jobEntity = jobDao.selectById(jobId);
        if (null == jobEntity) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }

        ResponseDTO<String> checkRes = this.checkParam(updateForm);
        if (!checkRes.getOk()) {
            return checkRes;
        }

        // 校验重复的执行类
        XsyJobEntity existJobClass = jobDao.selectByJobClass(updateForm.getJobClass());
        if (null != existJobClass && !existJobClass.getDeletedFlag() && !existJobClass.getJobId().equals(jobId)) {
            return ResponseDTO.userErrorParam("已经存在相同的执行类");
        }

        // 更新数据
        jobEntity = XsyBeanUtil.copy(updateForm, XsyJobEntity.class);
        jobDao.updateById(jobEntity);

        // 更新执行端
        XsyJobMsg jobMsg = new XsyJobMsg();
        jobMsg.setJobId(jobId);
        jobMsg.setMsgType(XsyJobMsg.MsgTypeEnum.UPDATE_JOB);
        jobMsg.setUpdateName(updateForm.getUpdateName());
        jobClientManager.publishToClient(jobMsg);
        return ResponseDTO.ok();
    }

    /**
     * 校验参数 如需其他校验，请自行添加校验逻辑
     *
     * @param addForm
     * @return
     */
    private ResponseDTO<String> checkParam(XsyJobAddForm addForm) {
        // 校验触发时间配置
        String triggerType = addForm.getTriggerType();
        String triggerValue = addForm.getTriggerValue();
        if (XsyJobTriggerTypeEnum.CRON.equalsValue(triggerType) && !XsyJobUtil.checkCron(triggerValue)) {
            return ResponseDTO.userErrorParam("cron表达式错误");
        }
        if (XsyJobTriggerTypeEnum.FIXED_DELAY.equalsValue(triggerType) && !XsyJobUtil.checkFixedDelay(triggerValue)) {
            return ResponseDTO.userErrorParam("固定间隔配置错误：必须是大于0的整数");
        }
        // 校验job class
        return XsyJobUtil.checkJobClass(addForm.getJobClass());
    }

    /**
     * 更新定时任务-是否开启
     *
     * @param updateForm
     * @return
     */
    public ResponseDTO<String> updateJobEnabled(XsyJobEnabledUpdateForm updateForm) {
        Integer jobId = updateForm.getJobId();
        XsyJobEntity jobEntity = jobDao.selectById(jobId);
        if (null == jobEntity) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        Boolean enabledFlag = updateForm.getEnabledFlag();
        if (Objects.equals(enabledFlag, jobEntity.getEnabledFlag())) {
            return ResponseDTO.ok();
        }
        // 更新数据
        jobEntity = new XsyJobEntity();
        jobEntity.setJobId(jobId);
        jobEntity.setEnabledFlag(enabledFlag);
        jobEntity.setUpdateName(updateForm.getUpdateName());
        jobDao.updateById(jobEntity);

        // 更新执行端
        XsyJobMsg jobMsg = new XsyJobMsg();
        jobMsg.setJobId(jobId);
        jobMsg.setMsgType(XsyJobMsg.MsgTypeEnum.UPDATE_JOB);
        jobMsg.setUpdateName(updateForm.getUpdateName());
        jobClientManager.publishToClient(jobMsg);
        return ResponseDTO.ok();
    }

    /**
     * 执行定时任务 忽略任务的开启状态,立即执行一次
     *
     * @param executeForm
     * @return
     */
    public ResponseDTO<String> execute(XsyJobExecuteForm executeForm) {
        Integer jobId = executeForm.getJobId();
        XsyJobEntity jobEntity = jobDao.selectById(jobId);
        if (null == jobEntity) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }

        // 更新执行端
        XsyJobMsg jobMsg = new XsyJobMsg();
        jobMsg.setJobId(jobId);
        jobMsg.setParam(executeForm.getParam());
        jobMsg.setMsgType(XsyJobMsg.MsgTypeEnum.EXECUTE_JOB);
        jobMsg.setUpdateName(executeForm.getUpdateName());
        jobClientManager.publishToClient(jobMsg);
        return ResponseDTO.ok();
    }

    /**
     * 移除定时任务 物理删除
     *
     * @return
     */
    public synchronized ResponseDTO<String> deleteJob(Integer jobId, RequestUser requestUser) {
        // 删除任务
        jobDao.updateDeletedFlag(jobId, Boolean.TRUE);

        // 更新执行端
        XsyJobMsg jobMsg = new XsyJobMsg();
        jobMsg.setJobId(jobId);
        jobMsg.setMsgType(XsyJobMsg.MsgTypeEnum.UPDATE_JOB);
        jobMsg.setUpdateName(requestUser.getUserName());
        jobClientManager.publishToClient(jobMsg);
        return ResponseDTO.ok();
    }

}
