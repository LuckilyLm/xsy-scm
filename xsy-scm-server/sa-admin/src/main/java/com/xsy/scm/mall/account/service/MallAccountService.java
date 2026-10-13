package com.xsy.scm.mall.account.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.account.constant.MallAccountStatusEnum;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.dao.MallCustomerAccountDao;
import com.xsy.scm.mall.account.domain.entity.MallCustomerAccountEntity;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.domain.form.MallAccountStatusForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordResetForm;
import com.xsy.scm.mall.account.domain.vo.MallAccountVO;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityPasswordService;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityLoginService;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MallAccountService {
    private final MallCustomerAccountDao accountDao;
    private final CustomerService customerService;
    private final SecurityLoginService securityLoginService;

    @Transactional(readOnly = true)
    public List<MallAccountVO> list(Long customerId) {
        customerService.requireInCurrentScope(customerId);
        return accountDao.listByCustomerId(customerId);
    }

    @Transactional(rollbackFor = Exception.class)
    public MallAccountVO create(MallAccountCreateForm form) {
        customerService.requireInCurrentScope(form.getCustomerId());
        String phone = normalizePhone(form.getLoginPhone());
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();
        MallCustomerAccountEntity row = new MallCustomerAccountEntity();
        row.setCustomerId(form.getCustomerId());
        row.setLoginPhone(phone);
        row.setPasswordHash(SecurityPasswordService.getEncryptPwd(form.getTemporaryPassword()));
        row.setStatus(MallAccountStatusEnum.ENABLED.name());
        row.setFirstPasswordChange(true);
        row.setLoginFailedCount(0);
        row.setVersion(0);
        row.setDeleted(false);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setCreatedBy(operator);
        row.setUpdatedBy(operator);
        try {
            accountDao.insert(row);
        } catch (DuplicateKeyException exception) {
            throw new ScmBusinessException(MallErrorCode.PHONE_DUPLICATE);
        }
        return accountDao.detailById(row.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(MallAccountStatusForm form) {
        MallCustomerAccountEntity row = lockVisible(form.getAccountId());
        String status = Boolean.TRUE.equals(form.getEnabled())
                ? MallAccountStatusEnum.ENABLED.name()
                : MallAccountStatusEnum.DISABLED.name();
        accountDao.updateStatus(row.getId(), status, ScmOperator.current());
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(MallPasswordResetForm form) {
        MallCustomerAccountEntity row = lockVisible(form.getAccountId());
        accountDao.resetPassword(row.getId(), SecurityPasswordService.getEncryptPwd(form.getTemporaryPassword()),
                ScmOperator.current());
        securityLoginService.removeLoginFail(row.getId(), UserTypeEnum.MALL_CUSTOMER);
    }

    private MallCustomerAccountEntity lockVisible(Long accountId) {
        MallCustomerAccountEntity row = accountDao.selectByIdForUpdate(accountId);
        if (row == null) {
            throw new ScmBusinessException(MallErrorCode.ACCOUNT_NOT_FOUND);
        }
        customerService.requireInCurrentScope(row.getCustomerId());
        return row;
    }

    static String normalizePhone(String phone) {
        String normalized = phone == null ? null : phone.trim().replace(" ", "");
        if (normalized == null || normalized.isEmpty()) {
            throw new ScmBusinessException(MallErrorCode.LOGIN_FAILED);
        }
        return normalized;
    }
}
