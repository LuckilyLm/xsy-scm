package com.xsy.scm.mall;

import com.xsy.scm.mall.catalog.dao.MallStockDao;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.domain.vo.MallStockFact;
import com.xsy.scm.mall.catalog.service.MallStockAvailabilityService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MallStockAvailabilityServiceTest {
    private final MallStockDao dao = mock(MallStockDao.class);
    private final MallStockAvailabilityService service = new MallStockAvailabilityService(dao);

    @Test
    void availableSalesQuantityUsesOnlySelectedWarehouseAndFrozenUnitFactor() {
        MallStockFact balance = fact("kg", "30.0000", "10.0000");
        when(dao.bySkus(11L, List.of(99L))).thenReturn(List.of(balance));
        when(dao.bySkus(12L, List.of(99L))).thenReturn(List.of());
        MallProductVO product = product();
        service.enrich(11L, List.of(product));
        assertThat(product.getAvailableQty()).isEqualByComparingTo("3.0000");
        assertThat(product.getStockStatus()).isEqualTo("AVAILABLE");
        assertThat(service.requireConversion(11L, 99L, "箱").stockUnitsPerSaleUnit())
                .isEqualByComparingTo("10.0000");

        MallProductVO elsewhere = product();
        service.enrich(12L, List.of(elsewhere));
        assertThat(elsewhere.getAvailableQty()).isEqualByComparingTo("0.0000");
        assertThat(elsewhere.getStockStatus()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void differentStockUnitWithoutConfiguredFactorIsNotSellable() {
        when(dao.bySkus(11L, List.of(99L))).thenReturn(List.of(fact("kg", "30.0000", null)));
        MallProductVO product = product();
        service.enrich(11L, List.of(product));
        assertThat(product.getStockStatus()).isEqualTo("UNKNOWN");
    }

    private static MallStockFact fact(String unit, String quantity, String factor) {
        MallStockFact fact = new MallStockFact();
        fact.setSkuId(99L);
        fact.setSaleUnit("箱");
        fact.setStockUnit(unit);
        fact.setAvailableStockQuantity(new BigDecimal(quantity));
        if (factor != null) fact.setStockUnitsPerSaleUnit(new BigDecimal(factor));
        return fact;
    }

    private static MallProductVO product() {
        MallProductVO product = new MallProductVO();
        product.setSkuId(99L);
        product.setSaleUnit("箱");
        product.setMinOrderQty(BigDecimal.ONE);
        return product;
    }
}
