package com.xsy.scm.admin.module.business.trace.manager;

import com.xsy.scm.admin.module.business.trace.dao.TraceBatchDao;
import com.xsy.scm.admin.module.business.trace.domain.entity.TraceBatchEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 溯源批次 Manager
 *
 * @author xsy-scm
 */
@Service
public class TraceBatchManager {

    @Resource
    private TraceBatchDao traceBatchDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(TraceBatchEntity entity) {
        traceBatchDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(TraceBatchEntity entity) {
        traceBatchDao.updateById(entity);
    }
}
