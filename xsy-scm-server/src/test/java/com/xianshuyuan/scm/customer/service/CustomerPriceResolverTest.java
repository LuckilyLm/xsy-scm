package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.customer.entity.CustomerAgreementPriceEntity;
import com.xianshuyuan.scm.customer.mapper.CustomerAgreementPriceMapper;
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
        var skuMapper = mock(ProductSkuMapper.class);
        var orderable = mock(OrderableSkuQueryService.class);
        var now = OffsetDateTime.parse("2026-09-03T10:00:00+08:00");
        var sku1 = sku(1, "9.0000"); var sku2 = sku(2, "8.0000");
        var agreement = new CustomerAgreementPriceEntity(); agreement.setId(7L); agreement.setSkuId(1L); agreement.setUnitPrice(new BigDecimal("6.5000"));
        given(orderable.requireOrderable(10L, List.of(1L, 2L))).willReturn(List.of(sku1, sku2));
        given(priceMapper.selectEffective(10L, List.of(1L, 2L), now)).willReturn(List.of(agreement));

        var result = new CustomerPriceResolver(customerService, priceMapper, skuMapper, orderable).resolve(10L, List.of(1L, 2L), now);

        assertThat(result).extracting(ResolvedCustomerPrice::unitPrice).containsExactly(new BigDecimal("6.5000"), new BigDecimal("8.0000"));
        assertThat(result).extracting(ResolvedCustomerPrice::source).containsExactly(PriceSource.AGREEMENT, PriceSource.MARKET);
    }

    @Test void returnsEmptyWithoutDatabaseCalls() {
        var customerService = mock(CustomerService.class);
        var priceMapper = mock(CustomerAgreementPriceMapper.class);
        var skuMapper = mock(ProductSkuMapper.class);
        var orderable = mock(OrderableSkuQueryService.class);

        var result = new CustomerPriceResolver(customerService, priceMapper, skuMapper, orderable)
            .resolve(10L, List.of(), OffsetDateTime.now());

        assertThat(result).isEmpty();
        verifyNoInteractions(priceMapper, orderable);
    }

    private ProductSkuEntity sku(long id, String price) { var sku = new ProductSkuEntity(); sku.setId(id); sku.setMarketPrice(new BigDecimal(price)); return sku; }
}
