package com.xsy.scm.admin.module.business.supplier.manager;

import com.xsy.scm.admin.module.business.supplier.dao.SupplierManufacturerDao;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierManufacturerEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 供应商厂商信息 Manager
 *
 * @author xsy-scm
 */
@Service
public class SupplierManufacturerManager {

    @Resource
    private SupplierManufacturerDao supplierManufacturerDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(SupplierManufacturerEntity entity) {
        supplierManufacturerDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(SupplierManufacturerEntity entity) {
        supplierManufacturerDao.updateById(entity);
    }
}
