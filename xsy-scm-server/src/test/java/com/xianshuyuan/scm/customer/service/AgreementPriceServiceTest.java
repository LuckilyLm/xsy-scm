package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.AgreementPriceSaveRequest;
import com.xianshuyuan.scm.customer.entity.CustomerAgreementPriceEntity;
import com.xianshuyuan.scm.customer.entity.AgreementPriceOperationLogEntity;
import com.xianshuyuan.scm.customer.mapper.AgreementPriceOperationLogMapper;
import com.xianshuyuan.scm.customer.mapper.CustomerAgreementPriceMapper;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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
        var service = service(prices, customers, skus, mock(AgreementPriceOperationLogMapper.class));
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
        var service = service(prices, mock(CustomerService.class), mock(ProductSkuMapper.class),
            mock(AgreementPriceOperationLogMapper.class));

        assertThatThrownBy(() -> service.delete(5L, 2))
            .isInstanceOf(BusinessException.class).hasMessageContaining("刷新后重试");
    }

    @Test void updateWritesAgreementPriceBeforeAndAfterAuditSnapshots() {
        var prices = mock(CustomerAgreementPriceMapper.class);
        var logs = mock(AgreementPriceOperationLogMapper.class);
        var existing = new CustomerAgreementPriceEntity();
        existing.setId(5L); existing.setCustomerId(3L); existing.setSkuId(8L);
        existing.setUnitPrice(new BigDecimal("5.0000")); existing.setVersion(1); existing.setDeleted(false);
        given(prices.selectById(5L)).willReturn(existing);
        given(prices.updateById(any(CustomerAgreementPriceEntity.class))).willReturn(1);
        var sku = new ProductSkuEntity(); sku.setId(8L);
        var skus = mock(ProductSkuMapper.class); given(skus.selectById(8L)).willReturn(sku);
        var service = service(prices, mock(CustomerService.class), skus, logs);

        service.update(5L, new AgreementPriceSaveRequest(1, 3L, 8L, new BigDecimal("6.5000"),
            OffsetDateTime.parse("2026-09-03T00:00:00Z"), null));

        var captor = ArgumentCaptor.forClass(AgreementPriceOperationLogEntity.class);
        verify(logs).insert(captor.capture());
        assertThat(captor.getValue().getBeforeData().get("unitPrice").asText()).isEqualTo("5.0000");
        assertThat(captor.getValue().getAfterData().get("unitPrice").asText()).isEqualTo("6.5000");
        assertThat(captor.getValue().getOperationType()).isEqualTo("UPDATE");
    }

    private AgreementPriceService service(CustomerAgreementPriceMapper prices, CustomerService customers,
                                           ProductSkuMapper skus, AgreementPriceOperationLogMapper logs) {
        return new AgreementPriceService(prices, customers, skus, new AgreementPriceValidator(), logs,
            new ObjectMapper().findAndRegisterModules());
    }

    private AgreementPriceSaveRequest request(Integer version) {
        return new AgreementPriceSaveRequest(version, 3L, 8L, new BigDecimal("5.0000"),
            OffsetDateTime.parse("2026-09-03T00:00:00Z"), null);
    }
}
