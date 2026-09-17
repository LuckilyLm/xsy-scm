package com.xsy.scm.admin.module.business.order.manager;

import com.xsy.scm.admin.module.business.order.dao.SaleRefundDao;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleRefundEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 销售退款单 Manager
 *
 * @author xsy-scm
 */
@Service
public class SaleRefundManager {

    @Resource
    private SaleRefundDao refundDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(SaleRefundEntity entity) {
        refundDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(SaleRefundEntity entity) {
        refundDao.updateById(entity);
    }
}
