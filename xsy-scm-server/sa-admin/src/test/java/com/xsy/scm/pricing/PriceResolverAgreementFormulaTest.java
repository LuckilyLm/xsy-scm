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
import com.xsy.scm.pricing.dao.CustomerGroupPriceDao;
import com.xsy.scm.pricing.dao.CustomerTypePriceDao;
import com.xsy.scm.pricing.dao.TimePriceDao;
import com.xsy.scm.pricing.dao.PricingCostBasisDao;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;
import com.xsy.scm.pricing.domain.entity.AgreementPriceEntity;
import com.xsy.scm.pricing.domain.entity.CustomerGroupPriceEntity;
import com.xsy.scm.pricing.domain.entity.CustomerTypePriceEntity;
import com.xsy.scm.pricing.domain.vo.PricingCostBasisVO;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.product.service.ProductBundleComponentQueryService;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
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
    private final CustomerGroupPriceDao groupPrices = mock(CustomerGroupPriceDao.class);
    private final CustomerTypePriceDao typePrices = mock(CustomerTypePriceDao.class);
    private final TimePriceDao timePrices = mock(TimePriceDao.class);
    private final ProductSkuOptionDao products = mock(ProductSkuOptionDao.class);
    private final ProductBundleComponentQueryService bundles = mock(ProductBundleComponentQueryService.class);
    private final CustomerSkuVisibilityDao visibility = mock(CustomerSkuVisibilityDao.class);
    private final PricingCostBasisDao costs = mock(PricingCostBasisDao.class);

    private final PriceResolver resolver = new PriceResolver(customers, customerTypes, agreements, groupPrices,
            typePrices, timePrices, products, bundles, visibility, costs);

    private AgreementPriceEntity agreement;

    private CustomerEntity customer;

    @BeforeEach
    void setup() {
        customer = new CustomerEntity();
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
        sku.setSpuId(30L);
        sku.setSaleUnit("斤");
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

    @Test
    void purchaseCostFormulaUsesLatestCommittedPurchaseBeforeWarehouseCost() {
        agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
        agreement.setFormulaParameter(new BigDecimal("0.1000"));
        var cost = cost("8.0000", "5.0000", "13.0000");
        when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost));
        when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                .thenReturn(List.of(latestPurchase("10.0000")));
        when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

        assertThat(price.getUnitPrice()).isEqualByComparingTo("13.0515");
        assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.PRICED);
        assertThat(price.getPriceBasisSnapshot()).containsEntry("basisSource", "LATEST_PURCHASE_PRICE")
                .containsEntry("basisUnitPrice", "10.0000")
                .containsEntry("latestPurchasePrice", "10.0000")
                .containsEntry("lossRate", "5.0000")
                .containsEntry("taxRate", "13.0000")
                .containsEntry("formulaType", "PURCHASE_COST_LOSS_TAX_MARKUP");
    }

    @Test
    void purchasePriceInAnotherUnitIsConvertedIntoSaleUnitBeforeApplyingFormula() {
        agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
        agreement.setFormulaParameter(new BigDecimal("0.1000"));
        when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost("8.0000", "5.0000", "13.0000")));
        var latest = latestPurchase("100.0000");
        latest.setPurchaseUnit("箱");
        latest.setSaleUnit("斤");
        // 1 斤 = 0.1 箱：100 元/箱折算 10 元/斤
        latest.setStockUnitsPerSaleUnit(new BigDecimal("0.1000"));
        when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                .thenReturn(List.of(latest));
        when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

        assertThat(price.getUnitPrice()).isEqualByComparingTo("13.0515");
        assertThat(price.getPriceBasisSnapshot())
                .containsEntry("basisSource", "LATEST_PURCHASE_PRICE")
                .containsEntry("basisUnitPrice", "10.0000")
                .containsEntry("latestPurchasePrice", "100.0000")
                .containsEntry("latestPurchaseUnit", "箱")
                .containsEntry("latestPurchasePriceSaleUnit", "10.0000");
    }

    @Test
    void purchasePriceInAnotherUnitWithoutConversionFallsBackToWarehouseCost() {
        agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
        agreement.setFormulaParameter(new BigDecimal("0.1000"));
        when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost("8.0000", "5.0000", "13.0000")));
        var latest = latestPurchase("100.0000");
        latest.setPurchaseUnit("箱");
        latest.setSaleUnit("斤");
        when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                .thenReturn(List.of(latest));
        when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

        assertThat(price.getUnitPrice()).isEqualByComparingTo("10.4412");
        assertThat(price.getPriceBasisSnapshot())
                .containsEntry("basisSource", "WAREHOUSE_WEIGHTED_AVG_COST")
                .containsEntry("basisUnitPrice", "8.0000")
                .containsEntry("latestPurchasePriceSaleUnit", null);
    }

    @Test
    void purchaseCostFormulaFallsBackToSaleUnitCostWhenLatestPurchaseMissing() {
        agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
        agreement.setFormulaParameter(new BigDecimal("0.1000"));
        when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost("8.0000", "5.0000", "13.0000")));
        when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                .thenReturn(List.of());
        when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

        assertThat(price.getUnitPrice()).isEqualByComparingTo("10.4412");
        assertThat(price.getPriceBasisSnapshot()).containsEntry("basisSource", "WAREHOUSE_WEIGHTED_AVG_COST")
                .containsEntry("basisUnitPrice", "8.0000");
    }

    @Test
    void purchaseCostFormulaIsUnpricedWhenProductRateIsMissing() {
        agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
        when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost("8.0000", null, "13.0000")));
        when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                .thenReturn(List.of(latestPurchase("10.0000")));
        when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

        assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.UNPRICED);
        assertThat(price.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.AGREEMENT_RESULT_INVALID);
    }

    @Test
    void purchaseCostFormulaIsUnpricedWhenBothLatestPurchaseAndWarehouseCostAreMissing() {
        agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
        when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost(null, "5.0000", "13.0000")));
        when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                .thenReturn(List.of());
        when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

        assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.UNPRICED);
        assertThat(price.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.AGREEMENT_COST_MISSING);
    }

    @Test
    void groupPriceAppliesByProductAndSaleUnitAboveTypePrice() {
        customer.setCustomerGroupId(9L);
        when(agreements.selectEffective(eq(8L), eq(List.of(40L)), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        var groupPrice = new CustomerGroupPriceEntity();
        groupPrice.setId(200L);
        groupPrice.setVersion(0);
        groupPrice.setGroupId(9L);
        groupPrice.setSpuId(30L);
        groupPrice.setSaleUnit("斤");
        groupPrice.setUnitPrice(new BigDecimal("12.0000"));
        when(groupPrices.selectEffective(eq(9L), eq(List.of(30L)), any(OffsetDateTime.class)))
                .thenReturn(List.of(groupPrice));
        var typePrice = new CustomerTypePriceEntity();
        typePrice.setId(300L);
        typePrice.setSkuId(40L);
        typePrice.setUnitPrice(new BigDecimal("18.0000"));
        when(typePrices.selectEffective(eq(5L), eq(List.of(40L)), any(OffsetDateTime.class)))
                .thenReturn(List.of(typePrice));

        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now()).getFirst();

        assertThat(price.getPriceSource()).isEqualTo(ScmPriceSourceEnum.CUSTOMER_GROUP);
        assertThat(price.getSourceRecordId()).isEqualTo(200L);
        assertThat(price.getUnitPrice()).isEqualByComparingTo("12");
    }

    @Test
    void groupPriceDoesNotCrossSaleUnits() {
        customer.setCustomerGroupId(9L);
        when(agreements.selectEffective(eq(8L), eq(List.of(40L)), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        var groupPrice = new CustomerGroupPriceEntity();
        groupPrice.setId(200L);
        groupPrice.setSpuId(30L);
        groupPrice.setSaleUnit("箱");
        groupPrice.setUnitPrice(new BigDecimal("99.0000"));
        when(groupPrices.selectEffective(eq(9L), eq(List.of(30L)), any(OffsetDateTime.class)))
                .thenReturn(List.of(groupPrice));

        var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now()).getFirst();

        // 斤与箱是不同单位，不做隐式换算，回落到市场价。
        assertThat(price.getPriceSource()).isEqualTo(ScmPriceSourceEnum.MARKET);
        assertThat(price.getUnitPrice()).isEqualByComparingTo("20");
    }

    private static PricingCostBasisVO cost(String saleUnitCost, String lossRate, String taxRate) {
        var cost = new PricingCostBasisVO();
        cost.setSkuId(40L);
        cost.setWarehouseId(7L);
        cost.setStockUnit("kg");
        cost.setStockUnitCost(saleUnitCost == null ? null : new BigDecimal(saleUnitCost));
        cost.setStockUnitsPerSaleUnit(BigDecimal.ONE);
        cost.setBalanceVersion(11);
        cost.setLossRate(lossRate == null ? null : new BigDecimal(lossRate));
        cost.setTaxRate(taxRate == null ? null : new BigDecimal(taxRate));
        return cost;
    }

    private static PricingCostBasisVO latestPurchase(String purchasePrice) {
        var price = new PricingCostBasisVO();
        price.setSkuId(40L);
        price.setLatestPurchasePrice(new BigDecimal(purchasePrice));
        price.setPurchaseUnit("斤");
        price.setSaleUnit("斤");
        return price;
    }
}
