package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.pricing.dao.AgreementPriceDao;
import com.xsy.scm.pricing.domain.entity.AgreementPriceEntity;
import com.xsy.scm.pricing.domain.form.AgreementPriceAddForm;
import com.xsy.scm.pricing.domain.form.AgreementPriceDeleteForm;
import com.xsy.scm.pricing.domain.form.AgreementPriceUpdateForm;
import com.xsy.scm.pricing.manager.PriceValidation;
import com.xsy.scm.pricing.constant.ScmPriceOperationTypeEnum;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.service.CustomerTypeService;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.util.ScmDecimalStrings;

import static com.xsy.scm.pricing.constant.PricingErrorCode.AGREEMENT_PRICE_NOT_FOUND;
import static com.xsy.scm.pricing.constant.PricingErrorCode.AGREEMENT_PRICE_OVERLAP;
import static com.xsy.scm.pricing.constant.PricingErrorCode.PRICE_BATCH_ROW_INVALID;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class AgreementPriceService {
    private final AgreementPriceDao agreementPriceDao;
    private final CustomerService customerService;
    private final CustomerTypeService customerTypeService;
    private final PriceValidation validation;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long add(AgreementPriceAddForm form) {
        validateAndLock(form, null);
        var entity = new AgreementPriceEntity();
        apply(entity, form);
        agreementPriceDao.insert(entity);
        log(entity, ScmPriceOperationTypeEnum.CREATE, null);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(AgreementPriceUpdateForm form) {
        // Lock old and new parents in ascending order if the dimension is changed.
        var existing = require(form.getAgreementPriceId(), form.getVersion());
        java.util.stream.Stream.of(existing.getCustomerId(), form.getCustomerId()).filter(Objects::nonNull).distinct()
                .sorted().forEach(agreementPriceDao::lockParent);
        existing = require(form.getAgreementPriceId(), form.getVersion());
        validateAndLock(form, existing.getId());
        String beforeSnapshot = snapshot(existing);
        apply(existing, form);
        existing.setVersion(form.getVersion());
        if (agreementPriceDao.updateById(existing) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        log(existing, ScmPriceOperationTypeEnum.UPDATE, beforeSnapshot);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(AgreementPriceDeleteForm form) {
        var agreementPrice = require(form.getAgreementPriceId(), form.getVersion());
        String beforeSnapshot = snapshot(agreementPrice);
        if (agreementPriceDao.softDelete(agreementPrice.getId(), form.getVersion(), ScmOperator.current()) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        agreementPrice.setDeleted(true);
        agreementPrice.setVersion(agreementPrice.getVersion() + 1);
        log(agreementPrice, ScmPriceOperationTypeEnum.DELETE, beforeSnapshot);
    }

    private AgreementPriceEntity require(Long agreementPriceId, Integer version) {
        var agreementPrice = agreementPriceDao.selectById(agreementPriceId);
        if (agreementPrice == null)
            throw new ScmBusinessException(AGREEMENT_PRICE_NOT_FOUND);
        if (!Objects.equals(version, agreementPrice.getVersion()))
            throw new ScmBusinessException(VERSION_CONFLICT);
        return agreementPrice;
    }

    private void validateAndLock(AgreementPriceAddForm form, Long excludedAgreementPriceId) {
        PriceValidation.amountAndPeriod(form.getUnitPrice(), form.getEffectiveFrom(), form.getEffectiveTo());
        if (form.getCustomerId() == null)
            throw new ScmBusinessException(PRICE_BATCH_ROW_INVALID);
        if (agreementPriceDao.lockParent(form.getCustomerId()) == null) {
            customerService.requireTradable(form.getCustomerId());
        }
        customerService.requireTradable(form.getCustomerId());
        validation.requireSellable(form.getSkuId());
        if (agreementPriceDao.countOverlapping(form.getCustomerId(), form.getSkuId(), form.getEffectiveFrom(),
                form.getEffectiveTo(), excludedAgreementPriceId) > 0)
            throw new ScmBusinessException(AGREEMENT_PRICE_OVERLAP);
    }

    private void apply(AgreementPriceEntity agreementPrice, AgreementPriceAddForm agreementPriceForm) {
        agreementPrice.setCustomerId(agreementPriceForm.getCustomerId());
        agreementPrice.setSkuId(agreementPriceForm.getSkuId());
        agreementPrice.setUnitPrice(ScmDecimalStrings.parseScale4(agreementPriceForm.getUnitPrice()));
        agreementPrice.setEffectiveFrom(agreementPriceForm.getEffectiveFrom());
        agreementPrice.setEffectiveTo(agreementPriceForm.getEffectiveTo());
        agreementPrice.setUpdatedAt(OffsetDateTime.now());
        agreementPrice.setUpdatedBy(ScmOperator.current());
        if (agreementPrice.getId() == null) {
            agreementPrice.setCreatedAt(agreementPrice.getUpdatedAt());
            agreementPrice.setCreatedBy(agreementPrice.getUpdatedBy());
        }
    }

    private String snapshot(AgreementPriceEntity agreementPrice) {
        var fields = new java.util.LinkedHashMap<
                String,
                Object>();
        fields.put("id", agreementPrice.getId());
        fields.put("customerId", agreementPrice.getCustomerId());
        fields.put("skuId", agreementPrice.getSkuId());
        fields.put("unitPrice", agreementPrice.getUnitPrice().setScale(4).toPlainString());
        fields.put("effectiveFrom", agreementPrice.getEffectiveFrom().toString());
        fields.put("effectiveTo",
                agreementPrice.getEffectiveTo() == null ? null : agreementPrice.getEffectiveTo().toString());
        fields.put("version", agreementPrice.getVersion());
        fields.put("deleted", agreementPrice.getDeleted());
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize price audit", ex);
        }
    }

    private void log(AgreementPriceEntity agreementPrice, ScmPriceOperationTypeEnum operationType,
            String beforeSnapshot) {
        agreementPriceDao.log(agreementPrice.getId(), operationType.name(), ScmOperator.current(), beforeSnapshot,
                snapshot(agreementPrice));
    }
}
