package com.xsy.scm.admin.module.business.customer.manager;

import com.xsy.scm.admin.module.business.customer.dao.CustomerDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户 Manager
 *
 * <p>负责客户持久化与事务编排。按分层约定，Service 调用本层，不直接操作 Dao。</p>
 *
 * @author xsy-scm
 */
@Service
public class CustomerManager {

    @Resource
    private CustomerDao customerDao;

    /**
     * 新增客户
     */
    @Transactional(rollbackFor = Throwable.class)
    public void save(CustomerEntity customerEntity) {
        customerDao.insert(customerEntity);
    }

    /**
     * 更新客户
     */
    @Transactional(rollbackFor = Throwable.class)
    public void update(CustomerEntity customerEntity) {
        customerDao.updateById(customerEntity);
    }
}
