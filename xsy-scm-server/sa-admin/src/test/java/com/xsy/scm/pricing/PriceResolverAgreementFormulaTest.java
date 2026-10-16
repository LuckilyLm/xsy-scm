package com.xsy.scm.pricing;

import com.xsy.scm.customer.constant.CustomerVisibilityPolicy;
import com.xsy.scm.customer.dao.CustomerSkuVisibilityDao;
import com.xsy.scm.customer.dao.CustomerTypeDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.entity.CustomerTypeEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.pricing.constant.ScmPriceStatusEnum;
import com.xsy.scm.pricing.constant.ScmUnpricedReasonEnum;
import com.xsy.scm.pricing.dao.AgreementPriceDao;
import com.xsy.scm.pricing.dao.CustomerTypePriceDao;
import com.xsy.scm.pricing.dao.PricingCostBasisDao;
import com.xsy.scm.pricing.domain.entity.AgreementPriceEntity;
import com.xsy.scm.pricing.domain.vo.PricingCostBasisVO;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.product.service.ProductBundleComponentQueryService;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 成本公式必须使用明确指定仓库的单位成本，不允许回退到默认仓、市场价或零成本。 */
class PriceResolverAgreementFormulaTest {

    private final CustomerService customers = mock(CustomerService.class);
    private final CustomerTypeDao customerTypes = mock(CustomerTypeDao.class);
    private final AgreementPriceDao agreements = mock(AgreementPriceDao.class);
    private final CustomerTypePriceDao typePrices = mock(CustomerTypePriceDao.class);
    private final ProductSkuOptionDao products = mock(ProductSkuOptionDao.class);
    private final ProductBundleComponentQueryService bundles = mock(ProductBundleComponentQueryService.class);
    private final CustomerSkuVisibilityDao visibility = mock(CustomerSkuVisibilityDao.class);
    private final PricingCostBasisDao costs = mock(PricingCostBasisDao.class);

    private final PriceResolver resolver = new PriceResolver(customers, customerTypes, agreements, typePrices,
            products, bundles, visibility, costs);

    private AgreementPriceEntity agreement;

    @BeforeEach
    void setup() {
        var customer = new CustomerEntity();
        customer.setId(8L);
        customer.setCustomerTypeId(5L);
        when(customers.requireTradable(8L)).thenReturn(customer);

        var customerType = new CustomerTypeEntity();
        customerType.setId(5L);
        customerType.setName("餐饮");
        customerType.setStatus("ENABLED");
        when(customerTypes.selectById(5L)).thenReturn(customerType);

        var sku = new ProductSkuOptionVO();
        sku.setSkuId(40L);
        sku.setSkuCode("SKU40");
        sku.setStatus("ON_SHELF");
        sku.setSpuStatus("ON_SHELF");
        sku.setCategoryStatus("ENABLED");
        sku.setMarketPrice(new BigDecimal("20.0000"));
        when(products.selectByIds(List.of(40L))).thenReturn(List.of(sku));

        agreement = new AgreementPriceEntity();
        agreement.setId(100L);
        agreement.setVersion(3);
        agreement.setSkuId(40L);
        agreement.setFormulaType("COST_MARKUP_RATE");
        agreement.setFormulaParameter(new BigDecimal("0.2000"));
        when(agreements.selectEffective(eq(8L), eq(List.of(40L)), any(OffsetDateTime.class)))
                .thenReturn(List.of(agreement));
        when(visibility.policy(8L)).thenReturn(CustomerVisibilityPolicy.ALL_ENABLED);
    }

    @Test
    void noExplicitWarehouseMustNotReadAnyDefaultOrInventCostQuote() {
        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), null).getFirst();

        assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.UNPRICED);
        assertThat(price.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.AGREEMENT_COST_MISSING);
        assertThat(price.getUnitPrice()).isNull();
        assertThat(price.getPriceBasisSnapshot()).isNull();
        verifyNoInteractions(costs, bundles);
    }

    @Test
    void explicitWarehouseCostIsConvertedIntoSalesUnitAndFrozenInBasis() {
        var cost = new PricingCostBasisVO();
        cost.setSkuId(40L);
        cost.setWarehouseId(7L);
        cost.setStockUnit("kg");
        cost.setStockUnitCost(new BigDecimal("5.0000"));
        cost.setStockUnitsPerSaleUnit(new BigDecimal("2.0000"));
        cost.setBalanceVersion(11);
        when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost));
        when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

        assertThat(price.getUnitPrice()).isEqualByComparingTo("12.0000");
        assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.PRICED);
        assertThat(price.getPriceBasisSnapshot()).containsEntry("warehouseId", "7")
                .containsEntry("stockUnitCost", "5.0000")
                .containsEntry("stockUnitsPerSaleUnit", "2.0000")
                .containsEntry("inventoryBalanceVersion", "11")
                .containsEntry("basisUnitPrice", "10.0000")
                .containsEntry("formulaType", "COST_MARKUP_RATE");
        verify(costs).byWarehouseAndSkus(7L, List.of(40L));
    }

    @Test
    void missingSelectedWarehouseCostIsUnpricedEvenWhenMarketPriceExists() {
        when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of());
        when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

        assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.UNPRICED);
        assertThat(price.getUnitPrice()).isNull();
        assertThat(price.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.AGREEMENT_COST_MISSING);
    }

    @Test
    void marketMarkupUsesOriginalMarketPriceAndNeverReadsWarehouseCost() {
        agreement.setFormulaType("MARKET_MARKUP_RATE");
        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), null).getFirst();

        assertThat(price.getUnitPrice()).isEqualByComparingTo("24.0000");
        assertThat(price.getPriceBasisSnapshot()).containsEntry("basisSource", "MARKET_PRICE")
                .containsEntry("basisUnitPrice", "20.0000");
        verifyNoInteractions(costs, bundles);
    }
}
