package com.xsy.scm.admin.module.business.stock.manager;

import com.xsy.scm.admin.module.business.stock.dao.StockCheckItemDao;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockCheckItemEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存盘点明细 Manager
 *
 * @author xsy-scm
 */
@Service
public class StockCheckItemManager {

    @Resource
    private StockCheckItemDao checkItemDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(StockCheckItemEntity entity) {
        checkItemDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(StockCheckItemEntity entity) {
        checkItemDao.updateById(entity);
    }
}
