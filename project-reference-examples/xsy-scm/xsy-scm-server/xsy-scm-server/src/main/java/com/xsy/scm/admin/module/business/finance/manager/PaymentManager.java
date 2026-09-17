package com.xsy.scm.admin.module.business.finance.manager;

import com.xsy.scm.admin.module.business.finance.dao.PaymentDao;
import com.xsy.scm.admin.module.business.finance.domain.entity.PaymentEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收款单 Manager
 *
 * <p>负责收款单持久化与事务编排，Service 调用本层，不直接操作 Dao。</p>
 *
 * @author xsy-scm
 */
@Service
public class PaymentManager {

    @Resource
    private PaymentDao paymentDao;

    /**
     * 新增收款单
     */
    @Transactional(rollbackFor = Throwable.class)
    public void save(PaymentEntity entity) {
        paymentDao.insert(entity);
    }

    /**
     * 更新收款单
     */
    @Transactional(rollbackFor = Throwable.class)
    public void update(PaymentEntity entity) {
        paymentDao.updateById(entity);
    }

    /**
     * 按主键查询
     */
    public PaymentEntity getById(Long paymentId) {
        return paymentDao.selectById(paymentId);
    }
}
