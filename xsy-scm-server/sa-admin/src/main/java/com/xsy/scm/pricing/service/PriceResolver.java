package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.xsy.scm.pricing.dao.AgreementPriceDao;
import com.xsy.scm.pricing.dao.CustomerTypePriceDao;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.domain.vo.PriceResolveResultVO;
import com.xsy.scm.pricing.domain.entity.AgreementPriceEntity;
import com.xsy.scm.pricing.domain.entity.CustomerTypePriceEntity;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;
import com.xsy.scm.pricing.constant.ScmPriceStatusEnum;
import com.xsy.scm.pricing.manager.PriceValidation;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.dao.CustomerTypeDao;
import com.xsy.scm.customer.dao.CustomerSkuVisibilityDao;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
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
    private final ProductSkuOptionDao productSkuOptionDao;
    private final CustomerSkuVisibilityDao customerSkuVisibilityDao;

    public PriceResolveResultVO preview(Long customerId, List<Long> skuIds, OffsetDateTime requestedAt) {
        var customer = customerService.requireTradable(customerId);
        var customerType = customerTypeDao.selectById(customer.getCustomerTypeId());
        if (customerType == null || !ScmEnableStatusEnum.ENABLED.name().equals(customerType.getStatus()))
            throw new ScmBusinessException(PRICE_RESOLVE_CUSTOMER_TYPE_MISSING);
        var priceAt = requestedAt == null ? OffsetDateTime.now() : requestedAt;
        return new PriceResolveResultVO(customerId, customerType.getId(), customerType.getName(), priceAt,
                resolve(customerId, skuIds, priceAt));
    }

    public List<ResolvedPriceVO> resolve(Long customerId, List<Long> skuIds, OffsetDateTime requestedAt) {
        if (skuIds.isEmpty()) return List.of();
        var customer = customerService.requireTradable(customerId);
        var customerType = customerTypeDao.selectById(customer.getCustomerTypeId());
        if (customerType == null || !ScmEnableStatusEnum.ENABLED.name().equals(customerType.getStatus()))
            throw new ScmBusinessException(PRICE_RESOLVE_CUSTOMER_TYPE_MISSING);
        var priceAt = requestedAt == null ? OffsetDateTime.now() : requestedAt;
        var skuOptionsById = productSkuOptionDao.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSkuOptionVO::getSkuId, Function.identity()));
        var agreementPricesBySkuId = agreementPriceDao.selectEffective(customerId, skuIds, priceAt).stream()
                .collect(Collectors.toMap(AgreementPriceEntity::getSkuId, Function.identity(), (first, next) -> first));
        var customerTypePricesBySkuId = customerTypePriceDao
                .selectEffective(customerType.getId(), skuIds, priceAt)
                .stream()
                .collect(Collectors.toMap(
                        CustomerTypePriceEntity::getSkuId, Function.identity(), (first, next) -> first));
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
            var unavailableReason = PriceValidation.unavailable(
                    skuOption, allEnabledVisibility || visibleSkuIds.contains(skuId));
            resolvedPrice.setUnavailableReason(unavailableReason);
            resolvedPrice.setSellable(unavailableReason == null);
            // Eligibility never short-circuits price lookup or erases a valid zero price.
            if (agreementPricesBySkuId.containsKey(skuId)) {
                var agreementPrice = agreementPricesBySkuId.get(skuId);
                resolvedPrice.price(
                        agreementPrice.getUnitPrice(), ScmPriceSourceEnum.AGREEMENT, agreementPrice.getId());
            } else if (customerTypePricesBySkuId.containsKey(skuId)) {
                var customerTypePrice = customerTypePricesBySkuId.get(skuId);
                resolvedPrice.price(customerTypePrice.getUnitPrice(), ScmPriceSourceEnum.CUSTOMER_TYPE,
                        customerTypePrice.getId());
            } else {
                resolvedPrice.price(skuOption == null ? null : skuOption.getMarketPrice(),
                        ScmPriceSourceEnum.MARKET, null);
            }
            return resolvedPrice;
        }).toList();
    }

    public List<ResolvedPriceVO> requireResolvable(Long customerId, List<Long> skuIds, OffsetDateTime requestedAt) {
        var resolvedPrices = resolve(customerId, skuIds, requestedAt);
        if (resolvedPrices.stream().anyMatch(resolvedPrice ->
                !resolvedPrice.isSellable() || resolvedPrice.getPriceStatus() == ScmPriceStatusEnum.UNPRICED))
            throw new ScmBusinessException(SKU_NOT_SELLABLE);
        return resolvedPrices;
    }
}
