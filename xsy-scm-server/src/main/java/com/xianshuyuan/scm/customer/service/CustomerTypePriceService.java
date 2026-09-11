package com.xianshuyuan.scm.customer.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.CustomerTypePricePageQuery;
import com.xianshuyuan.scm.customer.dto.CustomerTypePriceSaveRequest;
import com.xianshuyuan.scm.customer.entity.CustomerTypePriceEntity;
import com.xianshuyuan.scm.customer.mapper.CustomerTypeMapper;
import com.xianshuyuan.scm.customer.mapper.CustomerTypePriceMapper;
import com.xianshuyuan.scm.customer.vo.CustomerTypePriceResponse;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerTypePriceService {
    private final CustomerTypePriceMapper prices;
    private final CustomerTypeMapper customerTypes;
    private final ProductSkuMapper skus;
    private final CustomerTypePriceValidator validator;

    public PageData<CustomerTypePriceResponse> page(long page, long pageSize, Long customerTypeId,
                                                     Long skuId, String keyword) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        var result = prices.selectPricePage(new Page<>(page, pageSize),
                new CustomerTypePricePageQuery(page, pageSize, customerTypeId, skuId, normalized));
        return new PageData<>(result.getRecords().stream().map(this::response).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Transactional
    public long create(CustomerTypePriceSaveRequest request) {
        validate(request, null);
        CustomerTypePriceEntity entity = entity(request);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("SYSTEM");
        prices.insert(entity);
        return entity.getId();
    }

    @Transactional
    public void update(long id, CustomerTypePriceSaveRequest request) {
        require(id);
        if (request.version() == null) throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);
        validate(request, id);
        CustomerTypePriceEntity entity = entity(request);
        entity.setId(id);
        if (prices.updateById(entity) != 1) throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);
    }

    @Transactional
    public void delete(long id, int version) {
        require(id);
        if (prices.softDelete(id, version) != 1) throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);
    }

    private void validate(CustomerTypePriceSaveRequest request, Long id) {
        validator.validate(request);
        if (customerTypes.selectById(request.customerTypeId()) == null) {
            throw new BusinessException(CustomerErrorCodes.CUSTOMER_TYPE_NOT_FOUND);
        }
        if (skus.selectById(request.skuId()) == null) {
            throw new BusinessException(CustomerErrorCodes.SKU_NOT_VISIBLE);
        }
        prices.lockCustomerType(request.customerTypeId());
        if (prices.countOverlapping(id, request.customerTypeId(), request.skuId(),
                request.effectiveFrom(), request.effectiveTo()) > 0) {
            throw new BusinessException(CustomerErrorCodes.CUSTOMER_TYPE_PRICE_OVERLAP);
        }
    }

    private CustomerTypePriceEntity require(long id) {
        CustomerTypePriceEntity entity = prices.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new BusinessException(CustomerErrorCodes.CUSTOMER_TYPE_PRICE_NOT_FOUND);
        }
        return entity;
    }

    private CustomerTypePriceEntity entity(CustomerTypePriceSaveRequest request) {
        CustomerTypePriceEntity entity = new CustomerTypePriceEntity();
        entity.setVersion(request.version());
        entity.setCustomerTypeId(request.customerTypeId());
        entity.setSkuId(request.skuId());
        entity.setUnitPrice(request.unitPrice());
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());
        entity.setUpdatedBy("SYSTEM");
        return entity;
    }

    private CustomerTypePriceResponse response(CustomerTypePriceEntity entity) {
        return new CustomerTypePriceResponse(entity.getId(), entity.getVersion(), entity.getCustomerTypeId(),
                entity.getSkuId(), entity.getUnitPrice().setScale(4).toPlainString(),
                entity.getEffectiveFrom(), entity.getEffectiveTo());
    }
}
