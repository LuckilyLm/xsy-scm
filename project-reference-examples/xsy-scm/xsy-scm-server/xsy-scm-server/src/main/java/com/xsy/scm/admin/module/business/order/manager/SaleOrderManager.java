package com.xsy.scm.admin.module.business.order.manager;

import com.xsy.scm.admin.module.business.order.dao.SaleOrderDao;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 销售订单 Manager
 *
 * <p>负责订单持久化与事务编排。按分层约定，Service 调用本层，不直接操作 Dao。</p>
 *
 * @author xsy-scm
 */
@Service
public class SaleOrderManager {

    @Resource
    private SaleOrderDao saleOrderDao;

    /**
     * 新增订单
     */
    @Transactional(rollbackFor = Throwable.class)
    public void save(SaleOrderEntity saleOrderEntity) {
        saleOrderDao.insert(saleOrderEntity);
    }

    /**
     * 更新订单
     */
    @Transactional(rollbackFor = Throwable.class)
    public void update(SaleOrderEntity saleOrderEntity) {
        saleOrderDao.updateById(saleOrderEntity);
    }
}
