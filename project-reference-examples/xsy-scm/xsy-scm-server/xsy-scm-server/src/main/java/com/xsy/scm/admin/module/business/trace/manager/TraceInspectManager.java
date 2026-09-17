package com.xsy.scm.admin.module.business.trace.manager;

import com.xsy.scm.admin.module.business.trace.dao.TraceInspectDao;
import com.xsy.scm.admin.module.business.trace.domain.entity.TraceInspectEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 检测报告 Manager
 *
 * @author xsy-scm
 */
@Service
public class TraceInspectManager {

    @Resource
    private TraceInspectDao traceInspectDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(TraceInspectEntity entity) {
        traceInspectDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(TraceInspectEntity entity) {
        traceInspectDao.updateById(entity);
    }
}
