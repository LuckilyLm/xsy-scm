package com.xsy.scm.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.dao.CustomerSkuAliasDao;
import com.xsy.scm.customer.dao.CustomerSkuVisibilityDao;
import com.xsy.scm.customer.domain.entity.CustomerSkuAliasEntity;
import com.xsy.scm.customer.domain.form.CustomerSkuAliasForm;
import com.xsy.scm.customer.domain.vo.CustomerSkuAliasVO;
import com.xsy.scm.product.service.ProductQueryService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class CustomerSkuAliasService {
    private final CustomerSkuAliasDao aliasDao;
    private final CustomerSkuVisibilityDao visibilityDao;
    private final CustomerService customerService;
    private final ProductQueryService productQueryService;

    @Transactional(readOnly = true)
    public List<CustomerSkuAliasVO> list(Long customerId) {
        customerService.requireInCurrentScope(customerId);
        return aliasDao.listByCustomer(customerId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(CustomerSkuAliasForm form) {
        customerService.requireInCurrentScope(form.getCustomerId());
        if (visibilityDao.lockCustomer(form.getCustomerId()) == null) {
            throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        }
        String alias = form.getAlias() == null ? "" : form.getAlias().trim();
        if (alias.length() > 100 || !productQueryService.skuExists(form.getSkuId())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        CustomerSkuAliasEntity existing = aliasDao.selectOne(new LambdaQueryWrapper<CustomerSkuAliasEntity>()
            .eq(CustomerSkuAliasEntity::getCustomerId, form.getCustomerId())
            .eq(CustomerSkuAliasEntity::getSkuId, form.getSkuId()));
        if (existing == null) {
            if (form.getVersion() != null || alias.isEmpty()) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
            CustomerSkuAliasEntity row = new CustomerSkuAliasEntity();
            row.setCustomerId(form.getCustomerId());
            row.setSkuId(form.getSkuId());
            row.setAlias(alias);
            row.setCreatedAt(OffsetDateTime.now());
            row.setUpdatedAt(row.getCreatedAt());
            row.setCreatedBy(ScmOperator.current());
            row.setUpdatedBy(row.getCreatedBy());
            try {
                aliasDao.insert(row);
            } catch (DuplicateKeyException concurrentInsert) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
            return;
        }
        if (!Objects.equals(existing.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        if (alias.isEmpty()) {
            if (aliasDao.softDelete(existing.getId(), form.getCustomerId(), form.getVersion(),
                ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
            return;
        }
        if (alias.equals(existing.getAlias())) {
            return;
        }
        existing.setAlias(alias);
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(ScmOperator.current());
        if (aliasDao.updateById(existing) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }
}
