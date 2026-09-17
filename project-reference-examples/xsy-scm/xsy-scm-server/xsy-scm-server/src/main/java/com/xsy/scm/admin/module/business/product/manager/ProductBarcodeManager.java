package com.xsy.scm.admin.module.business.product.manager;

import com.xsy.scm.admin.module.business.product.dao.ProductBarcodeDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductBarcodeEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品条码 Manager
 *
 * @author xsy-scm
 */
@Service
public class ProductBarcodeManager {

    @Resource
    private ProductBarcodeDao productBarcodeDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(ProductBarcodeEntity entity) {
        productBarcodeDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ProductBarcodeEntity entity) {
        productBarcodeDao.updateById(entity);
    }
}
