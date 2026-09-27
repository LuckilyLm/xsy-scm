package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.pricing.dao.CustomerTypePriceDao;
import com.xsy.scm.pricing.domain.entity.CustomerTypePriceEntity;
import com.xsy.scm.pricing.domain.form.CustomerTypePriceAddForm;
import com.xsy.scm.pricing.domain.form.CustomerTypePriceDeleteForm;
import com.xsy.scm.pricing.domain.form.CustomerTypePriceUpdateForm;
import com.xsy.scm.pricing.manager.PriceValidation;
import com.xsy.scm.pricing.constant.ScmPriceOperationTypeEnum;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.service.CustomerTypeService;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.util.ScmDecimalStrings;

import static com.xsy.scm.pricing.constant.PricingErrorCode.CUSTOMER_TYPE_PRICE_NOT_FOUND;
import static com.xsy.scm.pricing.constant.PricingErrorCode.CUSTOMER_TYPE_PRICE_OVERLAP;
import static com.xsy.scm.pricing.constant.PricingErrorCode.PRICE_BATCH_ROW_INVALID;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class CustomerTypePriceService {
    private final CustomerTypePriceDao customerTypePriceDao;
    private final CustomerService customerService;
    private final CustomerTypeService customerTypeService;
    private final PriceValidation validation;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long add(CustomerTypePriceAddForm form) {
        validateAndLock(form, null);
        var entity = new CustomerTypePriceEntity();
        apply(entity, form);
        customerTypePriceDao.insert(entity);
        log(entity, ScmPriceOperationTypeEnum.CREATE, null);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerTypePriceUpdateForm form) {
        // Lock old and new parents in ascending order if the dimension is changed.
        var existing = require(form.getCustomerTypePriceId(), form.getVersion());
        java.util.stream.Stream.of(existing.getCustomerTypeId(), form.getCustomerTypeId()).filter(Objects::nonNull)
                .distinct().sorted().forEach(customerTypePriceDao::lockParent);
        existing = require(form.getCustomerTypePriceId(), form.getVersion());
        validateAndLock(form, existing.getId());
        String beforeSnapshot = snapshot(existing);
        apply(existing, form);
        existing.setVersion(form.getVersion());
        if (customerTypePriceDao.updateById(existing) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        log(existing, ScmPriceOperationTypeEnum.UPDATE, beforeSnapshot);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(CustomerTypePriceDeleteForm form) {
        var customerTypePrice = require(form.getCustomerTypePriceId(), form.getVersion());
        String beforeSnapshot = snapshot(customerTypePrice);
        if (customerTypePriceDao.softDelete(customerTypePrice.getId(), form.getVersion(), ScmOperator.current()) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        customerTypePrice.setDeleted(true);
        customerTypePrice.setVersion(customerTypePrice.getVersion() + 1);
        log(customerTypePrice, ScmPriceOperationTypeEnum.DELETE, beforeSnapshot);
    }

    private CustomerTypePriceEntity require(Long customerTypePriceId, Integer version) {
        var customerTypePrice = customerTypePriceDao.selectById(customerTypePriceId);
        if (customerTypePrice == null) throw new ScmBusinessException(CUSTOMER_TYPE_PRICE_NOT_FOUND);
        if (!Objects.equals(version, customerTypePrice.getVersion())) throw new ScmBusinessException(VERSION_CONFLICT);
        return customerTypePrice;
    }

    private void validateAndLock(CustomerTypePriceAddForm form, Long excludedCustomerTypePriceId) {
        PriceValidation.amountAndPeriod(form.getUnitPrice(), form.getEffectiveFrom(), form.getEffectiveTo());
        if (form.getCustomerTypeId() == null) throw new ScmBusinessException(PRICE_BATCH_ROW_INVALID);
        if (customerTypePriceDao.lockParent(form.getCustomerTypeId()) == null) {
            customerTypeService.requireSelectableType(form.getCustomerTypeId());
        }
        customerTypeService.requireSelectableType(form.getCustomerTypeId());
        validation.requireSellable(form.getSkuId());
        if (customerTypePriceDao.countOverlapping(form.getCustomerTypeId(), form.getSkuId(), form.getEffectiveFrom(),
                form.getEffectiveTo(), excludedCustomerTypePriceId) > 0)
            throw new ScmBusinessException(CUSTOMER_TYPE_PRICE_OVERLAP);
    }

    private void apply(CustomerTypePriceEntity customerTypePrice, CustomerTypePriceAddForm customerTypePriceForm) {
        customerTypePrice.setCustomerTypeId(customerTypePriceForm.getCustomerTypeId());
        customerTypePrice.setSkuId(customerTypePriceForm.getSkuId());
        customerTypePrice.setUnitPrice(ScmDecimalStrings.parseScale4(customerTypePriceForm.getUnitPrice()));
        customerTypePrice.setEffectiveFrom(customerTypePriceForm.getEffectiveFrom());
        customerTypePrice.setEffectiveTo(customerTypePriceForm.getEffectiveTo());
        customerTypePrice.setUpdatedAt(OffsetDateTime.now());
        customerTypePrice.setUpdatedBy(ScmOperator.current());
        if (customerTypePrice.getId() == null) {
            customerTypePrice.setCreatedAt(customerTypePrice.getUpdatedAt());
            customerTypePrice.setCreatedBy(customerTypePrice.getUpdatedBy());
        }
    }

    private String snapshot(CustomerTypePriceEntity customerTypePrice) {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("id", customerTypePrice.getId());
        fields.put("customerTypeId", customerTypePrice.getCustomerTypeId());
        fields.put("skuId", customerTypePrice.getSkuId());
        fields.put("unitPrice", customerTypePrice.getUnitPrice().setScale(4).toPlainString());
        fields.put("effectiveFrom", customerTypePrice.getEffectiveFrom().toString());
        fields.put("effectiveTo", customerTypePrice.getEffectiveTo() == null
                ? null : customerTypePrice.getEffectiveTo().toString());
        fields.put("version", customerTypePrice.getVersion());
        fields.put("deleted", customerTypePrice.getDeleted());
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize price audit", ex);
        }
    }

    private void log(CustomerTypePriceEntity customerTypePrice, ScmPriceOperationTypeEnum operationType,
                     String beforeSnapshot) {
        customerTypePriceDao.log(customerTypePrice.getId(), operationType.name(), ScmOperator.current(), beforeSnapshot,
                snapshot(customerTypePrice));
    }
}
