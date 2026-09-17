package com.xsy.scm.admin.module.business.finance.manager;

import com.xsy.scm.admin.module.business.finance.dao.InvoiceDao;
import com.xsy.scm.admin.module.business.finance.domain.entity.InvoiceEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发票 Manager
 *
 * @author xsy-scm
 */
@Service
public class InvoiceManager {

    @Resource
    private InvoiceDao invoiceDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(InvoiceEntity entity) {
        invoiceDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(InvoiceEntity entity) {
        invoiceDao.updateById(entity);
    }
}
