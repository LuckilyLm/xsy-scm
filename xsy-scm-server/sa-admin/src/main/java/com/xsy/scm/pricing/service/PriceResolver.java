package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.xsy.scm.pricing.dao.AgreementPriceDao;
import com.xsy.scm.pricing.dao.PricingCostBasisDao;
import com.xsy.scm.pricing.domain.vo.PricingCostBasisVO;
import com.xsy.scm.pricing.constant.AgreementFormulaType;
import com.xsy.scm.pricing.manager.AgreementFormulaCalculator;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.pricing.dao.CustomerTypePriceDao;
import com.xsy.scm.pricing.dao.TimePriceDao;
import com.xsy.scm.pricing.domain.entity.TimePriceEntity;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.domain.vo.PriceResolveResultVO;
import com.xsy.scm.pricing.domain.entity.AgreementPriceEntity;
import com.xsy.scm.pricing.domain.entity.CustomerTypePriceEntity;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;
import com.xsy.scm.pricing.constant.ScmPriceStatusEnum;
import com.xsy.scm.pricing.constant.ScmUnpricedReasonEnum;
import com.xsy.scm.pricing.manager.PriceValidation;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.dao.CustomerTypeDao;
import com.xsy.scm.customer.dao.CustomerSkuVisibilityDao;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.service.ProductBundleComponentQueryService;
import com.xsy.scm.product.domain.entity.ProductBundleComponentEntity;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.customer.constant.CustomerVisibilityPolicy;

import static com.xsy.scm.pricing.constant.PricingErrorCode.PRICE_RESOLVE_CUSTOMER_TYPE_MISSING;
import static com.xsy.scm.pricing.constant.PricingErrorCode.SKU_NOT_SELLABLE;

@Service
@RequiredArgsConstructor
public class PriceResolver {
    private final CustomerService customerService;
    private final CustomerTypeDao customerTypeDao;
    private final AgreementPriceDao agreementPriceDao;
    private final CustomerTypePriceDao customerTypePriceDao;
    private final TimePriceDao timePriceDao;
    private final ProductSkuOptionDao productSkuOptionDao;
    private final ProductBundleComponentQueryService productBundleDao;
    private final CustomerSkuVisibilityDao customerSkuVisibilityDao;
    private final PricingCostBasisDao costBasisDao;

    public PriceResolveResultVO preview(Long customerId, List<Long> skuIds, OffsetDateTime requestedAt) {
        return preview(customerId, skuIds, requestedAt, null);
    }

    public PriceResolveResultVO preview(Long customerId, List<Long> skuIds, OffsetDateTime requestedAt,
            Long warehouseId) {
        var customer = customerService.requireTradable(customerId);
        var customerType = customerTypeDao.selectById(customer.getCustomerTypeId());
        if (customerType == null || !ScmEnableStatusEnum.ENABLED.name().equals(customerType.getStatus()))
            throw new ScmBusinessException(PRICE_RESOLVE_CUSTOMER_TYPE_MISSING);
        var priceAt = requestedAt == null ? OffsetDateTime.now() : requestedAt;
        return new PriceResolveResultVO(customerId, customerType.getId(), customerType.getName(), priceAt,
                resolve(customerId, skuIds, priceAt, warehouseId));
    }

    public List<ResolvedPriceVO> resolve(Long customerId, List<Long> skuIds, OffsetDateTime requestedAt) {
        return resolve(customerId, skuIds, requestedAt, null);
    }

    public List<ResolvedPriceVO> resolve(Long customerId, List<Long> skuIds, OffsetDateTime requestedAt,
            Long warehouseId) {
        if (skuIds.isEmpty())
            return List.of();
        var customer = customerService.requireTradable(customerId);
        var customerType = customerTypeDao.selectById(customer.getCustomerTypeId());
        if (customerType == null || !ScmEnableStatusEnum.ENABLED.name().equals(customerType.getStatus()))
            throw new ScmBusinessException(PRICE_RESOLVE_CUSTOMER_TYPE_MISSING);
        var priceAt = requestedAt == null ? OffsetDateTime.now() : requestedAt;
        var skuOptionsById = productSkuOptionDao.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSkuOptionVO::getSkuId, Function.identity()));
        var agreementPricesBySkuId = agreementPriceDao.selectEffective(customerId, skuIds, priceAt).stream()
                .collect(Collectors.toMap(AgreementPriceEntity::getSkuId, Function.identity(), (first, next) -> first));
        var costFormulaSkuIds = agreementPricesBySkuId.values().stream()
                .filter(price -> AgreementFormulaType.COST_MARKUP_RATE.name().equals(price.getFormulaType()))
                .map(AgreementPriceEntity::getSkuId).toList();
        // 成本价只能来自订单显式选定的配送仓；缺少仓库时禁止使用任何默认仓成本。
        Long costWarehouseId = warehouseId;
        Map<Long,
                PricingCostBasisVO> costs = costFormulaSkuIds.isEmpty() || costWarehouseId == null
                        ? Map.of()
                        : costBasisDao.byWarehouseAndSkus(costWarehouseId, costFormulaSkuIds).stream()
                                .collect(Collectors.toMap(PricingCostBasisVO::getSkuId, Function.identity()));
        Map<Long,
                List<ProductBundleComponentEntity>> bundles = costFormulaSkuIds.isEmpty() || costWarehouseId == null
                        ? Map.of()
                        : productBundleDao.listByBundleSkus(costFormulaSkuIds).stream()
                                .collect(Collectors.groupingBy(ProductBundleComponentEntity::getBundleSkuId));
        List<Long> componentSkuIds = bundles.values().stream().flatMap(List::stream)
                .map(ProductBundleComponentEntity::getComponentSkuId).distinct().toList();
        Map<Long,
                PricingCostBasisVO> componentCosts = componentSkuIds.isEmpty()
                        ? Map.of()
                        : costBasisDao.byWarehouseAndSkus(costWarehouseId, componentSkuIds).stream()
                                .collect(Collectors.toMap(PricingCostBasisVO::getSkuId, Function.identity()));
        var customerTypePricesBySkuId = customerTypePriceDao.selectEffective(customerType.getId(), skuIds, priceAt)
                .stream().collect(Collectors.toMap(CustomerTypePriceEntity::getSkuId, Function.identity(),
                        (first, next) -> first));
        // 时价压在客户类型价之下、市场价之上：签了协议或分了类的客户不该被临时价顶掉
        var timePricesBySkuId = timePriceDao.selectEffective(skuIds, priceAt).stream()
                .collect(Collectors.toMap(TimePriceEntity::getSkuId, Function.identity(), (first, next) -> first));
        boolean allEnabledVisibility = CustomerVisibilityPolicy.ALL_ENABLED
                .equals(customerSkuVisibilityDao.policy(customerId));
        Set<Long> visibleSkuIds = allEnabledVisibility
                ? Set.of()
                : new HashSet<>(customerSkuVisibilityDao.visibleIds(customerId, skuIds));
        return skuIds.stream().map(skuId -> {
            var resolvedPrice = new ResolvedPriceVO();
            resolvedPrice.setSkuId(skuId);
            var skuOption = skuOptionsById.get(skuId);
            if (skuOption != null) {
                resolvedPrice.setSkuCode(skuOption.getSkuCode());
                resolvedPrice.setProductName(skuOption.getProductName());
                resolvedPrice.setSpecName(skuOption.getSpecName());
            }
            var unavailableReason = PriceValidation.unavailable(skuOption,
                    allEnabledVisibility || visibleSkuIds.contains(skuId));
            resolvedPrice.setUnavailableReason(unavailableReason);
            resolvedPrice.setSellable(unavailableReason == null);
            // Eligibility never short-circuits price lookup or erases a valid zero price.
            if (agreementPricesBySkuId.containsKey(skuId)) {
                var agreementPrice = agreementPricesBySkuId.get(skuId);
                AgreementFormulaType formula = agreementPrice.getFormulaType() == null
                        ? AgreementFormulaType.FIXED
                        : AgreementFormulaType.valueOf(agreementPrice.getFormulaType());
                PricingCostBasisVO cost = costs.get(skuId);
                BigDecimal saleUnitCost = cost == null || cost.getStockUnitCost() == null
                        || cost.getStockUnitCost().signum() <= 0 || cost.getStockUnitsPerSaleUnit() == null
                        || cost.getStockUnitsPerSaleUnit().signum() <= 0
                                ? null
                                : cost.getStockUnitCost().multiply(cost.getStockUnitsPerSaleUnit()).setScale(4,
                                        RoundingMode.HALF_UP);
                List<ProductBundleComponentEntity> bundle = bundles.get(skuId);
                List<Map<String, Object>> bundleBasis = new java.util.ArrayList<>();
                if (bundle != null) {
                    saleUnitCost = BigDecimal.ZERO;
                    for (ProductBundleComponentEntity component : bundle) {
                        PricingCostBasisVO child = componentCosts.get(component.getComponentSkuId());
                        if (child == null || child.getStockUnitCost() == null || child.getStockUnitCost().signum() <= 0
                                || child.getStockUnitsPerSaleUnit() == null
                                || child.getStockUnitsPerSaleUnit().signum() <= 0) {
                            saleUnitCost = null;
                            break;
                        }
                        BigDecimal contribution = child.getStockUnitCost().multiply(child.getStockUnitsPerSaleUnit())
                                .multiply(component.getQuantityPerBundleSaleUnit());
                        saleUnitCost = saleUnitCost.add(contribution);
                        Map<String, Object> detail = new LinkedHashMap<>();
                        detail.put("componentSkuId", component.getComponentSkuId().toString());
                        detail.put("componentVersion", component.getVersion().toString());
                        detail.put("stockUnit", child.getStockUnit());
                        detail.put("stockUnitCost", ScmDecimalStrings.format(child.getStockUnitCost()));
                        detail.put("stockUnitsPerSaleUnit", ScmDecimalStrings.format(child.getStockUnitsPerSaleUnit()));
                        detail.put("quantityPerBundleSaleUnit",
                                ScmDecimalStrings.format(component.getQuantityPerBundleSaleUnit()));
                        bundleBasis.add(detail);
                    }
                    if (saleUnitCost != null) {
                        saleUnitCost = saleUnitCost.setScale(4, RoundingMode.HALF_UP);
                    }
                }
                BigDecimal marketPrice = skuOption == null ? null : skuOption.getMarketPrice();
                BigDecimal calculated = AgreementFormulaCalculator.calculate(formula, agreementPrice.getUnitPrice(),
                        agreementPrice.getFormulaParameter(), marketPrice, saleUnitCost);
                resolvedPrice.price(calculated, ScmPriceSourceEnum.AGREEMENT, agreementPrice.getId());
                if (calculated == null) {
                    resolvedPrice
                            .setUnpricedReason(formula == AgreementFormulaType.COST_MARKUP_RATE && saleUnitCost == null
                                    ? ScmUnpricedReasonEnum.AGREEMENT_COST_MISSING
                                    : marketPrice == null && formula != AgreementFormulaType.FIXED
                                            ? ScmUnpricedReasonEnum.AGREEMENT_MARKET_PRICE_MISSING
                                            : ScmUnpricedReasonEnum.AGREEMENT_RESULT_INVALID);
                }
                if (calculated != null) {
                    Map<String, Object> basis = new LinkedHashMap<>();
                    basis.put("formulaType", formula.name());
                    basis.put("agreementPriceId", agreementPrice.getId().toString());
                    basis.put("agreementVersion", agreementPrice.getVersion().toString());
                    basis.put("formulaParameter", ScmDecimalStrings.format(agreementPrice.getFormulaParameter()));
                    basis.put("basisSource",
                            formula == AgreementFormulaType.COST_MARKUP_RATE
                                    ? "WAREHOUSE_WEIGHTED_AVG_COST"
                                    : formula == AgreementFormulaType.FIXED ? "FIXED" : "MARKET_PRICE");
                    basis.put("basisUnitPrice",
                            ScmDecimalStrings.format(formula == AgreementFormulaType.COST_MARKUP_RATE
                                    ? saleUnitCost
                                    : formula == AgreementFormulaType.FIXED
                                            ? agreementPrice.getUnitPrice()
                                            : marketPrice));
                    basis.put("warehouseId",
                            formula == AgreementFormulaType.COST_MARKUP_RATE ? costWarehouseId.toString() : null);
                    basis.put("stockUnit", cost == null ? null : cost.getStockUnit());
                    basis.put("stockUnitCost", cost == null ? null : ScmDecimalStrings.format(cost.getStockUnitCost()));
                    basis.put("stockUnitsPerSaleUnit",
                            cost == null ? null : ScmDecimalStrings.format(cost.getStockUnitsPerSaleUnit()));
                    basis.put("inventoryBalanceVersion", cost == null ? null : cost.getBalanceVersion().toString());
                    basis.put("bundleCostComponents", bundle == null ? null : bundleBasis);
                    basis.put("resultUnitPrice", ScmDecimalStrings.format(calculated));
                    basis.put("calculatedAt", priceAt.toString());
                    resolvedPrice.setPriceBasisSnapshot(basis);
                }
            } else if (customerTypePricesBySkuId.containsKey(skuId)) {
                var customerTypePrice = customerTypePricesBySkuId.get(skuId);
                resolvedPrice.price(customerTypePrice.getUnitPrice(), ScmPriceSourceEnum.CUSTOMER_TYPE,
                        customerTypePrice.getId());
            } else if (timePricesBySkuId.containsKey(skuId)) {
                var timePrice = timePricesBySkuId.get(skuId);
                resolvedPrice.price(timePrice.getUnitPrice(), ScmPriceSourceEnum.TIME_PRICE, timePrice.getId());
            } else {
                resolvedPrice.price(skuOption == null ? null : skuOption.getMarketPrice(), ScmPriceSourceEnum.MARKET,
                        null);
            }
            return resolvedPrice;
        }).toList();
    }

    public List<ResolvedPriceVO> requireResolvable(Long customerId, List<Long> skuIds, OffsetDateTime requestedAt) {
        return requireResolvable(customerId, skuIds, requestedAt, null);
    }

    public List<ResolvedPriceVO> requireResolvable(Long customerId, List<Long> skuIds, OffsetDateTime requestedAt,
            Long warehouseId) {
        var resolvedPrices = resolve(customerId, skuIds, requestedAt, warehouseId);
        if (resolvedPrices.stream().anyMatch(resolvedPrice -> !resolvedPrice.isSellable()
                || resolvedPrice.getPriceStatus() == ScmPriceStatusEnum.UNPRICED))
            throw new ScmBusinessException(SKU_NOT_SELLABLE);
        return resolvedPrices;
    }
}
