package com.xsy.scm.admin.module.business.stock.manager;

import com.xsy.scm.admin.module.business.stock.dao.StockCheckDao;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockCheckEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存盘点单 Manager
 *
 * @author xsy-scm
 */
@Service
public class StockCheckManager {

    @Resource
    private StockCheckDao checkDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(StockCheckEntity entity) {
        checkDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(StockCheckEntity entity) {
        checkDao.updateById(entity);
    }
}
