package com.xsy.scm.admin.module.business.customer.manager;

import com.xsy.scm.admin.module.business.customer.dao.CustomerProductAliasDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerProductAliasEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户商品别名 Manager
 *
 * @author xsy-scm
 */
@Service
public class CustomerProductAliasManager {

    @Resource
    private CustomerProductAliasDao customerProductAliasDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(CustomerProductAliasEntity entity) {
        customerProductAliasDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(CustomerProductAliasEntity entity) {
        customerProductAliasDao.updateById(entity);
    }
}
