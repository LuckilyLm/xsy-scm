package com.xsy.scm.admin.module.business.trace.manager;

import com.xsy.scm.admin.module.business.trace.dao.TraceCodeDao;
import com.xsy.scm.admin.module.business.trace.domain.entity.TraceCodeEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 溯源码 Manager
 *
 * @author xsy-scm
 */
@Service
public class TraceCodeManager {

    @Resource
    private TraceCodeDao traceCodeDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(TraceCodeEntity entity) {
        traceCodeDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(TraceCodeEntity entity) {
        traceCodeDao.updateById(entity);
    }
}
