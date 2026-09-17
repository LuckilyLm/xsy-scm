package com.xsy.scm.admin.module.business.supplier.manager;

import com.xsy.scm.admin.module.business.supplier.dao.SupplierProductApplyDao;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierProductApplyEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 供应商商品提报 Manager
 *
 * @author xsy-scm
 */
@Service
public class SupplierProductApplyManager {

    @Resource
    private SupplierProductApplyDao supplierProductApplyDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(SupplierProductApplyEntity entity) {
        supplierProductApplyDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(SupplierProductApplyEntity entity) {
        supplierProductApplyDao.updateById(entity);
    }
}
