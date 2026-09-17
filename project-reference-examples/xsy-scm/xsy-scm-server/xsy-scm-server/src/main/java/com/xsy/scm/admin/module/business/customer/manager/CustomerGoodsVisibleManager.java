package com.xsy.scm.admin.module.business.customer.manager;

import com.xsy.scm.admin.module.business.customer.dao.CustomerGoodsVisibleDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerGoodsVisibleEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户商品可见性 Manager
 *
 * @author xsy-scm
 */
@Service
public class CustomerGoodsVisibleManager {

    @Resource
    private CustomerGoodsVisibleDao goodsVisibleDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(CustomerGoodsVisibleEntity entity) {
        goodsVisibleDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(CustomerGoodsVisibleEntity entity) {
        goodsVisibleDao.updateById(entity);
    }
}
