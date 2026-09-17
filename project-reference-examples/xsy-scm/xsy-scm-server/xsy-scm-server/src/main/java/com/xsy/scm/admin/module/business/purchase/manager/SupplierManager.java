package com.xsy.scm.admin.module.business.purchase.manager;

import com.xsy.scm.admin.module.business.purchase.dao.SupplierDao;
import com.xsy.scm.admin.module.business.purchase.domain.entity.SupplierEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 供应商档案 Manager
 *
 * @author xsy-scm
 */
@Service
public class SupplierManager {

    @Resource
    private SupplierDao supplierDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(SupplierEntity entity) {
        supplierDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(SupplierEntity entity) {
        supplierDao.updateById(entity);
    }
}
