package com.xsy.scm.admin.module.business.stock.manager;

import com.xsy.scm.admin.module.business.stock.dao.StockAdjustDao;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockAdjustEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存调整单 Manager
 *
 * <p>负责库存调整单持久化与事务编排。按分层约定，Service 调用本层，不直接操作 Dao。</p>
 *
 * @author xsy-scm
 */
@Service
public class StockAdjustManager {

    @Resource
    private StockAdjustDao stockAdjustDao;

    /**
     * 新增库存调整单
     */
    @Transactional(rollbackFor = Throwable.class)
    public void save(StockAdjustEntity stockAdjustEntity) {
        stockAdjustDao.insert(stockAdjustEntity);
    }

    /**
     * 更新库存调整单
     */
    @Transactional(rollbackFor = Throwable.class)
    public void update(StockAdjustEntity stockAdjustEntity) {
        stockAdjustDao.updateById(stockAdjustEntity);
    }
}
