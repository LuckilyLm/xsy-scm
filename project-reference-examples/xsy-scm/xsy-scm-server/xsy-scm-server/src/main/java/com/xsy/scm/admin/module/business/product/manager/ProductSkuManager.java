package com.xsy.scm.admin.module.business.product.manager;

import com.xsy.scm.admin.module.business.product.dao.ProductSkuDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductSkuEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品规格 SKU Manager
 *
 * <p>负责 SKU 持久化与事务编排，Service 调用本层，不直接操作 Dao。</p>
 *
 * @author xsy-scm
 */
@Service
public class ProductSkuManager {

    @Resource
    private ProductSkuDao skuDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(ProductSkuEntity entity) {
        skuDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ProductSkuEntity entity) {
        skuDao.updateById(entity);
    }
}
