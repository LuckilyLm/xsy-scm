package com.xianshuyuan.scm.mall.service;

import com.xianshuyuan.scm.customer.service.PriceSource;
import com.xianshuyuan.scm.mall.vo.MallCheckoutItemResponse;
import com.xianshuyuan.scm.product.entity.ProductType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MallCheckoutFingerprintTest {
    private final MallCheckoutFingerprint fingerprints = new MallCheckoutFingerprint();

    @Test void fingerprintChangesWhenCustomerAddressQuantityOrPriceSourceChanges() {
        List<MallCheckoutItemResponse> baseline = List.of(item("2.0000", "6.2500", PriceSource.CUSTOMER_TYPE, 11L));
        String value = fingerprints.create(10L, 20L, baseline);

        assertThat(fingerprints.create(11L, 20L, baseline)).isNotEqualTo(value);
        assertThat(fingerprints.create(10L, 21L, baseline)).isNotEqualTo(value);
        assertThat(fingerprints.create(10L, 20L,
                List.of(item("3.0000", "6.2500", PriceSource.CUSTOMER_TYPE, 11L)))).isNotEqualTo(value);
        assertThat(fingerprints.create(10L, 20L,
                List.of(item("2.0000", "6.2500", PriceSource.AGREEMENT, 12L)))).isNotEqualTo(value);
    }

    private MallCheckoutItemResponse item(String quantity, String price, PriceSource source, Long sourceId) {
        return new MallCheckoutItemResponse(8L, "菠菜", "散装", "kg", ProductType.NON_STANDARD,
                quantity, price, source, sourceId, "12.5000");
    }
}
