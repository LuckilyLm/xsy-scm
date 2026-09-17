package com.xsy.scm.admin.module.business.screen.manager;

import com.xsy.scm.admin.module.business.screen.dao.ScreenConfigDao;
import com.xsy.scm.admin.module.business.screen.domain.entity.ScreenConfigEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据大屏配置 Manager
 *
 * @author xsy-scm
 */
@Service
public class ScreenConfigManager {

    @Resource
    private ScreenConfigDao screenConfigDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(ScreenConfigEntity entity) {
        screenConfigDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ScreenConfigEntity entity) {
        screenConfigDao.updateById(entity);
    }
}
