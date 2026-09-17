package com.xsy.scm.admin.module.business.order.manager;

import com.xsy.scm.admin.module.business.order.dao.SaleOrderItemDao;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderItemEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 销售订单明细 Manager
 *
 * @author xsy-scm
 */
@Service
public class SaleOrderItemManager {

    @Resource
    private SaleOrderItemDao itemDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(SaleOrderItemEntity entity) {
        itemDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(SaleOrderItemEntity entity) {
        itemDao.updateById(entity);
    }
}
