package com.xsy.scm.order.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.inventory.service.InventorySaleUnitConverter;
import com.xsy.scm.mall.catalog.service.MallStockAvailabilityService;
import com.xsy.scm.order.dao.OrderBundleComponentDao;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.manager.OrderAmountCalculator;
import com.xsy.scm.pricing.manager.PriceValidation;
import com.xsy.scm.product.service.ProductBundleComponentQueryService;
import com.xsy.scm.product.service.ProductSkuOptionQueryService;
import com.xsy.scm.product.domain.entity.ProductBundleComponentEntity;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.product.manager.BundleSalePriceAllocator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.xsy.scm.product.constant.ProductErrorCode.BUNDLE_COMPONENT_INVALID;

@Service
@RequiredArgsConstructor
public class OrderBundleSnapshotService {
    private final ProductBundleComponentQueryService productBundleDao;
    private final OrderBundleComponentDao orderBundleDao;
    private final ProductSkuOptionQueryService productSkuOptionDao;
    private final MallStockAvailabilityService stockAvailabilityService;

    public boolean freeze(SalesOrderItemEntity orderItem, Long warehouseId) {
        List<ProductBundleComponentEntity> components = productBundleDao.listByBundleSku(orderItem.getSkuId());
        if (components.isEmpty()) {
            return false;
        }
        if (orderItem.getActualQuantity() == null || orderItem.getActualQuantity().signum() <= 0
            || orderItem.getLockedUnitPrice() == null || warehouseId == null) {
            // 组合销售必须用本次配送仓，不能悄悄换成系统默认仓。
            throw new ScmBusinessException(BUNDLE_COMPONENT_INVALID);
        }
        BigDecimal bundleSaleAmount = OrderAmountCalculator.lineAmount(orderItem.getActualQuantity(),
            orderItem.getLockedUnitPrice());
        Long selectedWarehouseId = warehouseId;
        Map<Long,
            ProductSkuOptionVO> options = productSkuOptionDao
                .byIds(components.stream().map(ProductBundleComponentEntity::getComponentSkuId).toList()).stream()
                .collect(Collectors.toMap(ProductSkuOptionVO::getSkuId, Function.identity()));
        if (options.size() != components.size()) {
            throw new ScmBusinessException(BUNDLE_COMPONENT_INVALID);
        }
        // 全部组成规格验价、验单位与库存换算完成后，才开始写入组合数据。
        List<PreparedComponent> prepared = new ArrayList<>(components.size());
        for (ProductBundleComponentEntity component : components) {
            ProductSkuOptionVO option = options.get(component.getComponentSkuId());
            if (PriceValidation.unavailable(option, true) != null || option.getMarketPrice() == null
                || option.getMarketPrice().signum() <= 0) {
                throw new ScmBusinessException(BUNDLE_COMPONENT_INVALID);
            }
            var conversion = stockAvailabilityService.requireConversion(selectedWarehouseId,
                component.getComponentSkuId(), option.getSaleUnit());
            BigDecimal saleQuantity = orderItem.getActualQuantity().multiply(component.getQuantityPerBundleSaleUnit());
            try {
                saleQuantity = saleQuantity.setScale(4, RoundingMode.UNNECESSARY);
                InventorySaleUnitConverter.toStockQuantity(saleQuantity, conversion.stockUnit(),
                    conversion.stockUnitsPerSaleUnit());
            } catch (ArithmeticException invalidQuantity) {
                throw new ScmBusinessException(BUNDLE_COMPONENT_INVALID);
            }
            prepared.add(new PreparedComponent(component, option, conversion, saleQuantity));
        }
        List<BundleSalePriceAllocator.Share> shares;
        try {
            shares = BundleSalePriceAllocator.allocate(bundleSaleAmount,
                prepared.stream()
                    .map(item -> new BundleSalePriceAllocator.Component(item.component().getComponentSkuId(),
                        item.saleQuantity(), item.option().getMarketPrice()))
                    .toList());
        } catch (IllegalArgumentException invalidPriceAllocation) {
            throw new ScmBusinessException(BUNDLE_COMPONENT_INVALID);
        }
        for (int index = 0; index < prepared.size(); index++) {
            PreparedComponent item = prepared.get(index);
            ProductBundleComponentEntity component = item.component();
            ProductSkuOptionVO option = item.option();
            var conversion = item.conversion();
            var share = shares.get(index);
            OrderBundleComponentEntity snapshot = new OrderBundleComponentEntity();
            snapshot.setOrderId(orderItem.getOrderId());
            snapshot.setOrderItemId(orderItem.getId());
            snapshot.setWarehouseId(selectedWarehouseId);
            snapshot.setBundleSkuId(orderItem.getSkuId());
            snapshot.setComponentSkuId(component.getComponentSkuId());
            snapshot.setComponentSkuCodeSnapshot(option.getSkuCode());
            snapshot.setComponentProductNameSnapshot(option.getProductName());
            snapshot.setComponentSaleUnitSnapshot(option.getSaleUnit());
            snapshot.setComponentStockUnitSnapshot(conversion.stockUnit());
            snapshot.setQuantityPerBundleSaleUnit(component.getQuantityPerBundleSaleUnit());
            snapshot.setStockUnitsPerComponentSaleUnit(conversion.stockUnitsPerSaleUnit());
            snapshot.setComponentSaleQuantitySnapshot(item.saleQuantity());
            snapshot.setMarketUnitPriceSnapshot(option.getMarketPrice());
            snapshot.setMarketReferenceAmountSnapshot(share.referenceAmount());
            snapshot.setAllocatedSaleAmountSnapshot(share.allocatedSaleAmount());
            snapshot.setProductBundleComponentId(component.getId());
            snapshot.setProductBundleComponentVersion(component.getVersion());
            snapshot.setCreatedAt(OffsetDateTime.now());
            snapshot.setCreatedBy(ScmOperator.current());
            orderBundleDao.insert(snapshot);
        }
        return true;
    }

    private record PreparedComponent(ProductBundleComponentEntity component, ProductSkuOptionVO option,
        MallStockAvailabilityService.Conversion conversion, BigDecimal saleQuantity) {
    }
}
