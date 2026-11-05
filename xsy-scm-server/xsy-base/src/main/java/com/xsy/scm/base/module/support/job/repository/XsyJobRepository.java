package com.xsy.scm.base.module.support.job.repository;

import com.xsy.scm.base.module.support.job.repository.domain.XsyJobEntity;
import com.xsy.scm.base.module.support.job.repository.domain.XsyJobLogEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * job 持久化业务
 *
 */
@Service
public class XsyJobRepository {

    @Autowired
    private XsyJobDao jobDao;

    @Autowired
    private XsyJobLogDao jobLogDao;

    public XsyJobDao getJobDao() {
        return jobDao;
    }

    public XsyJobLogDao getJobLogDao() {
        return jobLogDao;
    }

    /**
     * 保存执行记录
     *
     * @param logEntity
     * @param jobEntity
     */
    @Transactional(rollbackFor = Throwable.class)
    public void saveLog(XsyJobLogEntity logEntity, XsyJobEntity jobEntity) {
        jobLogDao.insert(logEntity);

        jobEntity.setLastExecuteLogId(logEntity.getLogId());
        jobDao.updateById(jobEntity);
    }
}
