package com.xsy.scm.admin.module.business.external.manager;

import com.xsy.scm.admin.module.business.external.dao.ExternalSyncLogDao;
import com.xsy.scm.admin.module.business.external.domain.entity.ExternalSyncLogEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 外部平台同步日志 Manager
 *
 * @author xsy-scm
 */
@Service
public class ExternalSyncLogManager {

    @Resource
    private ExternalSyncLogDao externalSyncLogDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(ExternalSyncLogEntity entity) {
        externalSyncLogDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ExternalSyncLogEntity entity) {
        externalSyncLogDao.updateById(entity);
    }
}
