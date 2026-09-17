package com.xsy.scm.admin.module.business.supplier.manager;

import com.xsy.scm.admin.module.business.supplier.dao.SupplierStatementDao;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierStatementEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 供应商对账单 Manager
 *
 * @author xsy-scm
 */
@Service
public class SupplierStatementManager {

    @Resource
    private SupplierStatementDao supplierStatementDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(SupplierStatementEntity entity) {
        supplierStatementDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(SupplierStatementEntity entity) {
        supplierStatementDao.updateById(entity);
    }
}
