package com.xianshuyuan.scm.mall.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.mall.dto.MallAddressSaveRequest;
import com.xianshuyuan.scm.mall.entity.MallCustomerAddressEntity;
import com.xianshuyuan.scm.mall.mapper.MallCustomerAddressMapper;
import com.xianshuyuan.scm.mall.vo.MallAddressResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MallAddressService {

    private final MallCustomerAddressMapper addresses;

    public List<MallAddressResponse> list(long customerId) {
        return addresses.selectActiveByCustomerId(customerId).stream()
                .map(MallAddressService::toResponse)
                .toList();
    }

    @Transactional
    public MallAddressResponse create(long customerId, MallAddressSaveRequest request) {
        if (request.defaultAddress()) {
            addresses.clearDefault(customerId, null);
        }
        MallCustomerAddressEntity entity = new MallCustomerAddressEntity();
        entity.setCustomerId(customerId);
        apply(entity, request);
        entity.setStatus("ENABLED");
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("MALL");
        entity.setUpdatedBy("MALL");
        addresses.insert(entity);
        return toResponse(entity);
    }

    @Transactional
    public MallAddressResponse update(long customerId, long id, MallAddressSaveRequest request) {
        MallCustomerAddressEntity entity = require(customerId, id);
        if (request.defaultAddress()) {
            addresses.clearDefault(customerId, id);
        }
        apply(entity, request);
        entity.setUpdatedBy("MALL");
        addresses.updateById(entity);
        return toResponse(entity);
    }

    @Transactional
    public void delete(long customerId, long id) {
        require(customerId, id);
        addresses.softDeleteByIdAndCustomer(id, customerId);
    }

    @Transactional
    public MallAddressResponse setDefault(long customerId, long id) {
        MallCustomerAddressEntity entity = require(customerId, id);
        addresses.clearDefault(customerId, id);
        entity.setDefaultAddress(true);
        entity.setUpdatedBy("MALL");
        addresses.updateById(entity);
        return toResponse(entity);
    }

    public MallAddressResponse requireAddress(long customerId, Long addressId) {
        if (addressId == null) {
            throw new BusinessException(MallErrorCodes.ADDRESS_REQUIRED);
        }
        return toResponse(require(customerId, addressId));
    }

    private MallCustomerAddressEntity require(long customerId, long id) {
        MallCustomerAddressEntity entity = addresses.selectActiveByIdAndCustomer(id, customerId);
        if (entity == null) {
            throw new BusinessException(MallErrorCodes.ADDRESS_NOT_FOUND);
        }
        return entity;
    }

    private static void apply(MallCustomerAddressEntity entity, MallAddressSaveRequest request) {
        entity.setReceiverName(request.receiverName().trim());
        entity.setPhone(request.phone().trim());
        entity.setRegion(request.region().trim());
        entity.setDetailAddress(request.detailAddress().trim());
        entity.setDefaultAddress(request.defaultAddress());
    }

    private static MallAddressResponse toResponse(MallCustomerAddressEntity entity) {
        return new MallAddressResponse(entity.getId(), entity.getReceiverName(), entity.getPhone(),
                entity.getRegion(), entity.getDetailAddress(), Boolean.TRUE.equals(entity.getDefaultAddress()));
    }
}
