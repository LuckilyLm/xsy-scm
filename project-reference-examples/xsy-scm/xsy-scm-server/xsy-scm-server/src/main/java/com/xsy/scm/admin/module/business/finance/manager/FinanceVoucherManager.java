package com.xsy.scm.admin.module.business.finance.manager;

import com.xsy.scm.admin.module.business.finance.dao.FinanceVoucherDao;
import com.xsy.scm.admin.module.business.finance.dao.VoucherEntryDao;
import com.xsy.scm.admin.module.business.finance.domain.entity.FinanceVoucherEntity;
import com.xsy.scm.admin.module.business.finance.domain.entity.VoucherEntryEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会计凭证 Manager（凭证 + 分录原子落库）
 *
 * @author xsy-scm
 */
@Service
public class FinanceVoucherManager {

    @Resource
    private FinanceVoucherDao financeVoucherDao;

    @Resource
    private VoucherEntryDao voucherEntryDao;

    /**
     * 保存凭证与分录
     */
    @Transactional(rollbackFor = Throwable.class)
    public void saveWithEntries(FinanceVoucherEntity voucher, List<VoucherEntryEntity> entries) {
        financeVoucherDao.insert(voucher);
        for (VoucherEntryEntity entry : entries) {
            entry.setVoucherId(voucher.getVoucherId());
            entry.setDeletedFlag(Boolean.FALSE);
            voucherEntryDao.insert(entry);
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(FinanceVoucherEntity voucher) {
        financeVoucherDao.updateById(voucher);
    }
}
