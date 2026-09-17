package com.xsy.scm.admin.module.business.product.manager;

import com.xsy.scm.admin.module.business.product.dao.ProductPriceDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductPriceEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品价格 Manager
 *
 * <p>负责价格持久化与事务编排，Service 调用本层，不直接操作 Dao。</p>
 *
 * @author xsy-scm
 */
@Service
public class ProductPriceManager {

    @Resource
    private ProductPriceDao priceDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(ProductPriceEntity entity) {
        priceDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ProductPriceEntity entity) {
        priceDao.updateById(entity);
    }
}
