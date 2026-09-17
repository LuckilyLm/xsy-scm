package com.xsy.scm.admin.module.business.customer.manager;

import com.xsy.scm.admin.module.business.customer.dao.CustomerDiscountDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerDiscountEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户折扣率 Manager
 *
 * @author xsy-scm
 */
@Service
public class CustomerDiscountManager {

    @Resource
    private CustomerDiscountDao customerDiscountDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(CustomerDiscountEntity entity) {
        customerDiscountDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(CustomerDiscountEntity entity) {
        customerDiscountDao.updateById(entity);
    }
}
