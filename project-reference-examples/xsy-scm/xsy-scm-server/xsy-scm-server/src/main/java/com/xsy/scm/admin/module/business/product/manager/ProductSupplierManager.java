package com.xsy.scm.admin.module.business.product.manager;

import com.xsy.scm.admin.module.business.product.dao.ProductSupplierDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductSupplierEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品-供应商关系 Manager
 *
 * <p>负责关系持久化与事务编排，Service 调用本层，不直接操作 Dao。</p>
 *
 * @author xsy-scm
 */
@Service
public class ProductSupplierManager {

    @Resource
    private ProductSupplierDao productSupplierDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(ProductSupplierEntity entity) {
        productSupplierDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ProductSupplierEntity entity) {
        productSupplierDao.updateById(entity);
    }
}
