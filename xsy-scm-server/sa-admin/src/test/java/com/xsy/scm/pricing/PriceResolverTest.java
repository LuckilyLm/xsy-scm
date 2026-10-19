package com.xsy.scm.pricing;

import org.junit.jupiter.api.Test;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.constant.*;
import com.xsy.scm.pricing.manager.PriceValidation;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;

class PriceResolverTest {
    @Test
    void unavailableDoesNotMeanUnpriced() {
        var r = new ResolvedPriceVO();
        r.setSellable(false);
        r.setUnavailableReason(ScmUnavailableReasonEnum.SKU_OFF_SHELF);
        r.price(new BigDecimal("5.0000"), ScmPriceSourceEnum.MARKET, null);
        assertThat(r.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.PRICED);
        assertThat(r.getUnitPrice()).isEqualByComparingTo("5");
        assertThat(r.getUnpricedReason()).isNull();
        assertThat(r.isSellable()).isFalse();
    }

    @Test
    void zeroIsPricedAndMissingSourceAloneIsUnpriced() {
        var r = new ResolvedPriceVO();
        r.price(BigDecimal.ZERO, ScmPriceSourceEnum.MARKET, null);
        assertThat(r.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.PRICED);
        r.price(null, ScmPriceSourceEnum.MARKET, null);
        assertThat(r.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.UNPRICED);
        assertThat(r.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.NO_PRICE_SOURCE);
        assertThat(r.getUnitPrice()).isNull();
        assertThat(r.getPriceSource()).isNull();
        // 新公式可以引入更细的未定价原因，但不影响缺少价格来源的既有语义。
        assertThat(ScmUnpricedReasonEnum.values()).contains(ScmUnpricedReasonEnum.NO_PRICE_SOURCE);
        // 时价（TIME_PRICE）是新增来源：压在客户类型价之下、市场价之上
        assertThat(ScmPriceSourceEnum.values()).hasSize(4);
        assertThat(ScmPriceSourceEnum.values()).contains(ScmPriceSourceEnum.TIME_PRICE);
    }

    @Test
    void fiveUnavailableReasonsAreIndependent() {
        assertThat(PriceValidation.unavailable(null, true)).isEqualTo(ScmUnavailableReasonEnum.SKU_NOT_FOUND);
        var s = new ProductSkuOptionVO();
        assertThat(PriceValidation.unavailable(s, true)).isEqualTo(ScmUnavailableReasonEnum.SKU_OFF_SHELF);
        s.setStatus("ON_SHELF");
        assertThat(PriceValidation.unavailable(s, true)).isEqualTo(ScmUnavailableReasonEnum.SPU_OFF_SHELF);
        s.setSpuStatus("ON_SHELF");
        assertThat(PriceValidation.unavailable(s, true)).isEqualTo(ScmUnavailableReasonEnum.CATEGORY_DISABLED);
        s.setCategoryStatus("ENABLED");
        assertThat(PriceValidation.unavailable(s, false)).isEqualTo(ScmUnavailableReasonEnum.NOT_VISIBLE);
        assertThat(PriceValidation.unavailable(s, true)).isNull();
    }

    @Test
    void periodsAndDecimalLimits() {
        var from = OffsetDateTime.parse("2026-09-01T00:00:00Z");
        PriceValidation.amountAndPeriod("0.0000", from, null);
        assertThatThrownBy(() -> PriceValidation.amountAndPeriod("-1", from, null)).hasMessageContaining("价格");
        assertThatThrownBy(() -> PriceValidation.amountAndPeriod("1.00001", from, null)).hasMessageContaining("价格");
        assertThatThrownBy(() -> PriceValidation.amountAndPeriod("1", from, from)).hasMessageContaining("结束时间");
    }
}
