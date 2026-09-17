package com.xsy.scm.admin.module.business.purchase.manager;

import com.xsy.scm.admin.module.business.purchase.dao.PurchaseItemDao;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseItemEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 采购明细 Manager
 *
 * @author xsy-scm
 */
@Service
public class PurchaseItemManager {

    @Resource
    private PurchaseItemDao itemDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(PurchaseItemEntity entity) {
        itemDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(PurchaseItemEntity entity) {
        itemDao.updateById(entity);
    }
}
