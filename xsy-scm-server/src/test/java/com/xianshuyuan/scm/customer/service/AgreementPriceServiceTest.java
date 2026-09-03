package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.AgreementPriceSaveRequest;
import com.xianshuyuan.scm.customer.entity.CustomerAgreementPriceEntity;
import com.xianshuyuan.scm.customer.mapper.CustomerAgreementPriceMapper;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class AgreementPriceServiceTest {
    @Test void locksCustomerBeforeCheckingOverlap() {
        var prices = mock(CustomerAgreementPriceMapper.class);
        var customers = mock(CustomerService.class);
        var skus = mock(ProductSkuMapper.class);
        var sku = new ProductSkuEntity(); sku.setId(8L);
        given(skus.selectById(8L)).willReturn(sku);
        doAnswer(invocation -> {
            CustomerAgreementPriceEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        }).when(prices).insert(any(CustomerAgreementPriceEntity.class));
        var service = new AgreementPriceService(prices, customers, skus, new AgreementPriceValidator());
        var request = request(null);

        service.create(request);

        var order = inOrder(prices);
        order.verify(prices).lockCustomer(3L);
        order.verify(prices).countOverlapping(null, 3L, 8L, request.effectiveFrom(), request.effectiveTo());
    }

    @Test void agreementDeleteUsesAtomicIdVersionPredicate() {
        var prices = mock(CustomerAgreementPriceMapper.class);
        var entity = new CustomerAgreementPriceEntity(); entity.setId(5L); entity.setDeleted(false);
        given(prices.selectById(5L)).willReturn(entity);
        given(prices.softDelete(5L, 2)).willReturn(0);
        var service = new AgreementPriceService(prices, mock(CustomerService.class),
            mock(ProductSkuMapper.class), new AgreementPriceValidator());

        assertThatThrownBy(() -> service.delete(5L, 2))
            .isInstanceOf(BusinessException.class).hasMessageContaining("刷新后重试");
    }

    private AgreementPriceSaveRequest request(Integer version) {
        return new AgreementPriceSaveRequest(version, 3L, 8L, new BigDecimal("5.0000"),
            OffsetDateTime.parse("2026-09-03T00:00:00Z"), null);
    }
}
