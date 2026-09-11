package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.CustomerTypePriceSaveRequest;
import com.xianshuyuan.scm.customer.entity.CustomerTypeEntity;
import com.xianshuyuan.scm.customer.entity.CustomerTypePriceEntity;
import com.xianshuyuan.scm.customer.mapper.CustomerTypeMapper;
import com.xianshuyuan.scm.customer.mapper.CustomerTypePriceMapper;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class CustomerTypePriceServiceTest {

    @Test void createRejectsOverlappingHalfOpenPeriodBeforeInsert() {
        CustomerTypePriceMapper prices = mock(CustomerTypePriceMapper.class);
        given(prices.countOverlapping(null, 3L, 8L, from(), to())).willReturn(1);
        CustomerTypePriceService service = service(prices);

        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CustomerErrorCodes.CUSTOMER_TYPE_PRICE_OVERLAP));

        verify(prices, never()).insert(any(CustomerTypePriceEntity.class));
    }

    @Test void updateRequiresMatchingVersionAndPersistsTypedPrice() {
        CustomerTypePriceMapper prices = mock(CustomerTypePriceMapper.class);
        CustomerTypePriceEntity existing = entity(5L, 1);
        given(prices.selectById(5L)).willReturn(existing);
        given(prices.updateById(any(CustomerTypePriceEntity.class))).willReturn(1);
        CustomerTypePriceService service = service(prices);

        service.update(5L, request(1));

        ArgumentCaptor<CustomerTypePriceEntity> captor = ArgumentCaptor.forClass(CustomerTypePriceEntity.class);
        verify(prices).updateById(captor.capture());
        assertThat(captor.getValue().getCustomerTypeId()).isEqualTo(3L);
        assertThat(captor.getValue().getUnitPrice()).isEqualByComparingTo("6.2500");
    }

    @Test void deleteUsesAtomicIdAndVersionPredicate() {
        CustomerTypePriceMapper prices = mock(CustomerTypePriceMapper.class);
        given(prices.selectById(5L)).willReturn(entity(5L, 2));
        given(prices.softDelete(5L, 2)).willReturn(0);

        assertThatThrownBy(() -> service(prices).delete(5L, 2))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CustomerErrorCodes.VERSION_CONFLICT));
    }

    private CustomerTypePriceService service(CustomerTypePriceMapper prices) {
        CustomerTypeMapper types = mock(CustomerTypeMapper.class);
        ProductSkuMapper skus = mock(ProductSkuMapper.class);
        CustomerTypeEntity type = new CustomerTypeEntity(); type.setId(3L);
        ProductSkuEntity sku = new ProductSkuEntity(); sku.setId(8L);
        given(types.selectById(3L)).willReturn(type);
        given(skus.selectById(8L)).willReturn(sku);
        return new CustomerTypePriceService(prices, types, skus, new CustomerTypePriceValidator());
    }

    private CustomerTypePriceSaveRequest request(Integer version) {
        return new CustomerTypePriceSaveRequest(version, 3L, 8L, new BigDecimal("6.2500"), from(), to());
    }

    private CustomerTypePriceEntity entity(long id, int version) {
        CustomerTypePriceEntity entity = new CustomerTypePriceEntity();
        entity.setId(id); entity.setVersion(version); entity.setDeleted(false);
        entity.setCustomerTypeId(3L); entity.setSkuId(8L); entity.setUnitPrice(new BigDecimal("6.2500"));
        entity.setEffectiveFrom(from()); entity.setEffectiveTo(to());
        return entity;
    }

    private OffsetDateTime from() { return OffsetDateTime.parse("2026-09-11T00:00:00+08:00"); }
    private OffsetDateTime to() { return OffsetDateTime.parse("2026-10-01T00:00:00+08:00"); }
}
