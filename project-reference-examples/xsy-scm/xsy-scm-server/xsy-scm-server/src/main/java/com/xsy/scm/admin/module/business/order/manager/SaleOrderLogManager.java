package com.xsy.scm.admin.module.business.order.manager;

import com.xsy.scm.admin.module.business.order.dao.SaleOrderLogDao;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderLogEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单操作日志 Manager
 *
 * <p>日志仅写入，不提供修改与删除。</p>
 *
 * @author xsy-scm
 */
@Service
public class SaleOrderLogManager {

    @Resource
    private SaleOrderLogDao logDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(SaleOrderLogEntity entity) {
        logDao.insert(entity);
    }
}
