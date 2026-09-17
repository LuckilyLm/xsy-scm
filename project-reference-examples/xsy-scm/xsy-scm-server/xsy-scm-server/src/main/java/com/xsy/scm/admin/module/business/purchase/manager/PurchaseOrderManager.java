package com.xsy.scm.admin.module.business.purchase.manager;

import com.xsy.scm.admin.module.business.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseOrderEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 采购单 Manager
 *
 * <p>负责采购单持久化与事务编排。按分层约定，Service 调用本层，不直接操作 Dao。</p>
 *
 * @author xsy-scm
 */
@Service
public class PurchaseOrderManager {

    @Resource
    private PurchaseOrderDao purchaseOrderDao;

    /**
     * 新增采购单
     */
    @Transactional(rollbackFor = Throwable.class)
    public void save(PurchaseOrderEntity purchaseOrderEntity) {
        purchaseOrderDao.insert(purchaseOrderEntity);
    }

    /**
     * 更新采购单
     */
    @Transactional(rollbackFor = Throwable.class)
    public void update(PurchaseOrderEntity purchaseOrderEntity) {
        purchaseOrderDao.updateById(purchaseOrderEntity);
    }
}
