package com.xsy.scm.admin.module.business.finance.manager;

import com.xsy.scm.admin.module.business.finance.dao.ExternalConfigDao;
import com.xsy.scm.admin.module.business.finance.domain.entity.ExternalConfigEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 外部系统配置 Manager
 *
 * @author xsy-scm
 */
@Service
public class ExternalConfigManager {

    @Resource
    private ExternalConfigDao externalConfigDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(ExternalConfigEntity entity) {
        externalConfigDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ExternalConfigEntity entity) {
        externalConfigDao.updateById(entity);
    }
}
