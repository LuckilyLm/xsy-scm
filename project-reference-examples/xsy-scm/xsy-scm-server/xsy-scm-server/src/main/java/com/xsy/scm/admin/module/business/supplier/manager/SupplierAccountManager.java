package com.xsy.scm.admin.module.business.supplier.manager;

import com.xsy.scm.admin.module.business.supplier.dao.SupplierAccountDao;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierAccountEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 供应商账号 Manager
 *
 * @author xsy-scm
 */
@Service
public class SupplierAccountManager {

    @Resource
    private SupplierAccountDao supplierAccountDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(SupplierAccountEntity entity) {
        supplierAccountDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(SupplierAccountEntity entity) {
        supplierAccountDao.updateById(entity);
    }
}
