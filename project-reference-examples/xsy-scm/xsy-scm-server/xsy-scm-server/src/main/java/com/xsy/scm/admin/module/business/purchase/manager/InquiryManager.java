package com.xsy.scm.admin.module.business.purchase.manager;

import com.xsy.scm.admin.module.business.purchase.dao.InquiryDao;
import com.xsy.scm.admin.module.business.purchase.dao.InquiryItemDao;
import com.xsy.scm.admin.module.business.purchase.dao.InquiryQuoteDao;
import com.xsy.scm.admin.module.business.purchase.domain.entity.InquiryEntity;
import com.xsy.scm.admin.module.business.purchase.domain.entity.InquiryItemEntity;
import com.xsy.scm.admin.module.business.purchase.domain.entity.InquiryQuoteEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 询价 Manager
 *
 * <p>聚合询价单 / 明细 / 报价三表的原子操作（多表事务）。</p>
 *
 * @author xsy-scm
 */
@Service
public class InquiryManager {

    @Resource
    private InquiryDao inquiryDao;

    @Resource
    private InquiryItemDao inquiryItemDao;

    @Resource
    private InquiryQuoteDao inquiryQuoteDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(InquiryEntity entity) {
        inquiryDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(InquiryEntity entity) {
        inquiryDao.updateById(entity);
    }

    /**
     * 覆盖式保存明细：先逻辑删除原明细，再插入新明细
     */
    @Transactional(rollbackFor = Throwable.class)
    public void replaceItems(Long inquiryId, List<InquiryItemEntity> items) {
        inquiryItemDao.batchUpdateDeletedByInquiryId(inquiryId);
        for (InquiryItemEntity item : items) {
            item.setDeletedFlag(Boolean.FALSE);
            inquiryItemDao.insert(item);
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    public void saveQuote(InquiryQuoteEntity quote) {
        inquiryQuoteDao.insert(quote);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void updateQuote(InquiryQuoteEntity quote) {
        inquiryQuoteDao.updateById(quote);
    }
}
