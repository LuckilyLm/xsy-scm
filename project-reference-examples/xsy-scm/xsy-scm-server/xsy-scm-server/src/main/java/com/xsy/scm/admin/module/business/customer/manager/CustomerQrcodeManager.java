package com.xsy.scm.admin.module.business.customer.manager;

import com.xsy.scm.admin.module.business.customer.dao.CustomerQrcodeDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerQrcodeEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务员推广二维码 Manager
 *
 * @author xsy-scm
 */
@Service
public class CustomerQrcodeManager {

    @Resource
    private CustomerQrcodeDao qrcodeDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(CustomerQrcodeEntity entity) {
        qrcodeDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(CustomerQrcodeEntity entity) {
        qrcodeDao.updateById(entity);
    }
}
