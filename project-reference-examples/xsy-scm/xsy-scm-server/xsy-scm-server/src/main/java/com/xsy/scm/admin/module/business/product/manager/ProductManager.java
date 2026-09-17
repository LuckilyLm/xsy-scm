package com.xsy.scm.admin.module.business.product.manager;

import com.xsy.scm.admin.module.business.product.dao.ProductDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品 Manager
 *
 * <p>负责商品持久化与事务编排。按分层约定，Service 调用本层，不直接操作 Dao。</p>
 *
 * @author xsy-scm
 */
@Service
public class ProductManager {

    @Resource
    private ProductDao productDao;

    /**
     * 新增商品
     */
    @Transactional(rollbackFor = Throwable.class)
    public void save(ProductEntity productEntity) {
        productDao.insert(productEntity);
    }

    /**
     * 更新商品
     */
    @Transactional(rollbackFor = Throwable.class)
    public void update(ProductEntity productEntity) {
        productDao.updateById(productEntity);
    }
}
