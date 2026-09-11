package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.customer.entity.CustomerAgreementPriceEntity;
import com.xianshuyuan.scm.customer.entity.CustomerEntity;
import com.xianshuyuan.scm.customer.entity.CustomerTypePriceEntity;
import com.xianshuyuan.scm.customer.mapper.CustomerAgreementPriceMapper;
import com.xianshuyuan.scm.customer.mapper.CustomerTypePriceMapper;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class CustomerPriceResolverTest {
    @Test void choosesAgreementPriceAndFallsBackToMarketPriceInBatch() {
        var customerService = mock(CustomerService.class);
        var priceMapper = mock(CustomerAgreementPriceMapper.class);
        var typePriceMapper = mock(CustomerTypePriceMapper.class);
        var skuMapper = mock(ProductSkuMapper.class);
        var orderable = mock(OrderableSkuQueryService.class);
        var now = OffsetDateTime.parse("2026-09-03T10:00:00+08:00");
        var sku1 = sku(1, "9.0000"); var sku2 = sku(2, "8.0000");
        var agreement = new CustomerAgreementPriceEntity(); agreement.setId(7L); agreement.setSkuId(1L); agreement.setUnitPrice(new BigDecimal("6.5000"));
        var customer = new CustomerEntity(); customer.setId(10L); customer.setCustomerTypeId(3L);
        given(customerService.requireEnabled(10L)).willReturn(customer);
        given(orderable.requireOrderable(10L, List.of(1L, 2L))).willReturn(List.of(sku1, sku2));
        given(priceMapper.selectEffective(10L, List.of(1L, 2L), now)).willReturn(List.of(agreement));

        var result = new CustomerPriceResolver(customerService, priceMapper, typePriceMapper, skuMapper, orderable).resolve(10L, List.of(1L, 2L), now);

        assertThat(result).extracting(ResolvedCustomerPrice::unitPrice).containsExactly(new BigDecimal("6.5000"), new BigDecimal("8.0000"));
        assertThat(result).extracting(ResolvedCustomerPrice::source).containsExactly(PriceSource.AGREEMENT, PriceSource.MARKET);
    }

    @Test void choosesCustomerTypePriceBeforeFallingBackToMarketPrice() {
        var customerService = mock(CustomerService.class);
        var agreementPrices = mock(CustomerAgreementPriceMapper.class);
        var typePrices = mock(CustomerTypePriceMapper.class);
        var skuMapper = mock(ProductSkuMapper.class);
        var orderable = mock(OrderableSkuQueryService.class);
        var now = OffsetDateTime.parse("2026-09-03T10:00:00+08:00");
        var customer = new CustomerEntity(); customer.setId(10L); customer.setCustomerTypeId(3L);
        var typePrice = new CustomerTypePriceEntity();
        typePrice.setId(11L); typePrice.setSkuId(1L); typePrice.setUnitPrice(new BigDecimal("7.2000"));
        given(customerService.requireEnabled(10L)).willReturn(customer);
        given(orderable.requireOrderable(10L, List.of(1L, 2L))).willReturn(List.of(sku(1, "9.0000"), sku(2, "8.0000")));
        given(agreementPrices.selectEffective(10L, List.of(1L, 2L), now)).willReturn(List.of());
        given(typePrices.selectEffective(3L, List.of(1L, 2L), now)).willReturn(List.of(typePrice));

        var result = new CustomerPriceResolver(customerService, agreementPrices, typePrices, skuMapper, orderable)
                .resolve(10L, List.of(1L, 2L), now);

        assertThat(result).extracting(ResolvedCustomerPrice::unitPrice)
                .containsExactly(new BigDecimal("7.2000"), new BigDecimal("8.0000"));
        assertThat(result).extracting(ResolvedCustomerPrice::source)
                .containsExactly(PriceSource.CUSTOMER_TYPE, PriceSource.MARKET);
        assertThat(result.getFirst().sourceRecordId()).isEqualTo(11L);
    }

    @Test void returnsEmptyWithoutDatabaseCalls() {
        var customerService = mock(CustomerService.class);
        var priceMapper = mock(CustomerAgreementPriceMapper.class);
        var typePriceMapper = mock(CustomerTypePriceMapper.class);
        var skuMapper = mock(ProductSkuMapper.class);
        var orderable = mock(OrderableSkuQueryService.class);

        var result = new CustomerPriceResolver(customerService, priceMapper, typePriceMapper, skuMapper, orderable)
            .resolve(10L, List.of(), OffsetDateTime.now());

        assertThat(result).isEmpty();
        verifyNoInteractions(priceMapper, orderable);
    }

    private ProductSkuEntity sku(long id, String price) { var sku = new ProductSkuEntity(); sku.setId(id); sku.setMarketPrice(new BigDecimal(price)); return sku; }
}
