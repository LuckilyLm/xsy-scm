package com.xsy.scm.admin.module.business.customer.manager;

import com.xsy.scm.admin.module.business.customer.dao.CustomerPeriodDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerPeriodEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户账期 Manager
 *
 * @author xsy-scm
 */
@Service
public class CustomerPeriodManager {

    @Resource
    private CustomerPeriodDao periodDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(CustomerPeriodEntity entity) {
        periodDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(CustomerPeriodEntity entity) {
        periodDao.updateById(entity);
    }
}
