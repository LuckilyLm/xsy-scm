package com.xsy.scm.admin.module.business.purchase.manager;

import com.xsy.scm.admin.module.business.purchase.dao.ReceiveDao;
import com.xsy.scm.admin.module.business.purchase.domain.entity.ReceiveEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 采购收货单 Manager
 *
 * @author xsy-scm
 */
@Service
public class ReceiveManager {

    @Resource
    private ReceiveDao receiveDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(ReceiveEntity entity) {
        receiveDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ReceiveEntity entity) {
        receiveDao.updateById(entity);
    }
}
