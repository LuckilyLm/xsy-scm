package com.xsy.scm.admin.module.business.external.manager;

import com.xsy.scm.admin.module.business.external.dao.ExternalMappingDao;
import com.xsy.scm.admin.module.business.external.domain.entity.ExternalMappingEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 外部平台映射 Manager
 *
 * @author xsy-scm
 */
@Service
public class ExternalMappingManager {

    @Resource
    private ExternalMappingDao externalMappingDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(ExternalMappingEntity entity) {
        externalMappingDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ExternalMappingEntity entity) {
        externalMappingDao.updateById(entity);
    }
}
