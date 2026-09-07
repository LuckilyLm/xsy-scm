package com.xianshuyuan.scm.customer.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.converter.CustomerConverter;
import com.xianshuyuan.scm.customer.dto.*;
import com.xianshuyuan.scm.customer.entity.CustomerAgreementPriceEntity;
import com.xianshuyuan.scm.customer.entity.AgreementPriceOperationLogEntity;
import com.xianshuyuan.scm.customer.mapper.AgreementPriceOperationLogMapper;
import com.xianshuyuan.scm.customer.mapper.CustomerAgreementPriceMapper;
import com.xianshuyuan.scm.customer.vo.AgreementPriceResponse;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AgreementPriceService {
    private final CustomerAgreementPriceMapper prices;
    private final CustomerService customers;
    private final ProductSkuMapper skus;
    private final AgreementPriceValidator validator;
    private final AgreementPriceOperationLogMapper logs;
    private final ObjectMapper json;

    public AgreementPriceService(CustomerAgreementPriceMapper prices, CustomerService customers,
                                 ProductSkuMapper skus, AgreementPriceValidator validator,
                                 AgreementPriceOperationLogMapper logs, ObjectMapper json) {
        this.prices = prices;
        this.customers = customers;
        this.skus = skus;
        this.validator = validator;
        this.logs = logs;
        this.json = json;
    }

    public PageData<AgreementPriceResponse> page(long page, long pageSize, Long customerId, Long skuId, String keyword) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        var result = prices.selectAgreementPage(new Page<>(page, pageSize),
                new AgreementPricePageQuery(page, pageSize, customerId, skuId, normalizedKeyword));
        return new PageData<>(result.getRecords().stream().map(this::response).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Transactional
    public long create(AgreementPriceSaveRequest request) {
        validate(request, null);
        var entity = CustomerConverter.toPrice(request);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("SYSTEM");
        prices.insert(entity);
        log(entity.getId(), "CREATE", null, snapshot(entity));
        return entity.getId();
    }

    @Transactional
    public void update(long id, AgreementPriceSaveRequest request) {
        var existing = require(id);
        var before = snapshot(existing);
        if (request.version() == null) throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);
        validate(request, id);
        var entity = CustomerConverter.toPrice(request);
        entity.setId(id);
        if (prices.updateById(entity) != 1) throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);
        log(id, "UPDATE", before, snapshot(entity));
    }

    @Transactional
    public void delete(long id, int version) {
        var existing = require(id);
        var before = snapshot(existing);
        if (prices.softDelete(id, version) != 1) throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);
        var after = snapshot(existing);
        ((ObjectNode) after).put("deleted", true);
        ((ObjectNode) after).put("version", version + 1);
        log(id, "DELETE", before, after);
    }

    private void validate(AgreementPriceSaveRequest request, Long id) {
        validator.validate(request);
        customers.require(request.customerId());
        if (skus.selectById(request.skuId()) == null) throw new BusinessException(CustomerErrorCodes.SKU_NOT_VISIBLE);
        // Locking the stable customer row serializes all agreement writes for this customer and
        // protects the subsequent overlap check without requiring PostgreSQL extensions.
        prices.lockCustomer(request.customerId());
        if (prices.countOverlapping(id, request.customerId(), request.skuId(),
                request.effectiveFrom(), request.effectiveTo()) > 0) {
            throw new BusinessException(CustomerErrorCodes.AGREEMENT_PRICE_OVERLAP);
        }
    }

    private CustomerAgreementPriceEntity require(long id) {
        var entity = prices.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new BusinessException(CustomerErrorCodes.AGREEMENT_PRICE_NOT_FOUND);
        }
        return entity;
    }

    private AgreementPriceResponse response(CustomerAgreementPriceEntity entity) {
        return new AgreementPriceResponse(entity.getId(), entity.getVersion(), entity.getCustomerId(),
                entity.getSkuId(), entity.getUnitPrice().setScale(4).toPlainString(),
                entity.getEffectiveFrom(), entity.getEffectiveTo());
    }

    private JsonNode snapshot(CustomerAgreementPriceEntity entity) {
        ObjectNode node = json.createObjectNode();
        if (entity.getId() == null) node.putNull("id"); else node.put("id", entity.getId());
        node.put("customerId", entity.getCustomerId());
        node.put("skuId", entity.getSkuId());
        if (entity.getUnitPrice() == null) node.putNull("unitPrice"); else node.put("unitPrice", entity.getUnitPrice().setScale(4).toPlainString());
        if (entity.getEffectiveFrom() == null) node.putNull("effectiveFrom"); else node.put("effectiveFrom", entity.getEffectiveFrom().toString());
        if (entity.getEffectiveTo() == null) node.putNull("effectiveTo"); else node.put("effectiveTo", entity.getEffectiveTo().toString());
        node.put("version", entity.getVersion());
        node.put("deleted", Boolean.TRUE.equals(entity.getDeleted()));
        return node;
    }

    private void log(long agreementPriceId, String operationType, JsonNode before, JsonNode after) {
        var log = new AgreementPriceOperationLogEntity();
        log.setAgreementPriceId(agreementPriceId);
        log.setOperationType(operationType);
        log.setOperator("SYSTEM");
        log.setBeforeData(before);
        log.setAfterData(after);
        log.setCreatedBy("SYSTEM");
        logs.insert(log);
    }
}
