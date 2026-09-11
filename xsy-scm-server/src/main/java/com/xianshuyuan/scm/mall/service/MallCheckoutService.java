package com.xianshuyuan.scm.mall.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.service.CustomerPriceResolver;
import com.xianshuyuan.scm.customer.service.ResolvedCustomerPrice;
import com.xianshuyuan.scm.mall.dto.MallCheckoutItemRequest;
import com.xianshuyuan.scm.mall.dto.MallCheckoutPreviewRequest;
import com.xianshuyuan.scm.mall.dto.MallOrderSubmitRequest;
import com.xianshuyuan.scm.mall.entity.MallOrderAddressEntity;
import com.xianshuyuan.scm.mall.mapper.MallCatalogMapper;
import com.xianshuyuan.scm.mall.mapper.MallOrderAddressMapper;
import com.xianshuyuan.scm.mall.row.MallProductRow;
import com.xianshuyuan.scm.mall.vo.MallAddressResponse;
import com.xianshuyuan.scm.mall.vo.MallCheckoutItemResponse;
import com.xianshuyuan.scm.mall.vo.MallCheckoutPreviewResponse;
import com.xianshuyuan.scm.mall.vo.MallOrderSubmitResponse;
import com.xianshuyuan.scm.order.converter.SalesOrderConverter;
import com.xianshuyuan.scm.order.dto.SalesOrderItemSaveRequest;
import com.xianshuyuan.scm.order.dto.SalesOrderSaveRequest;
import com.xianshuyuan.scm.order.entity.OrderSource;
import com.xianshuyuan.scm.order.service.OrderAmountCalculator;
import com.xianshuyuan.scm.order.service.SalesOrderApplicationService;
import com.xianshuyuan.scm.order.vo.SalesOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商城结算。价格一律由服务端解析，提交前用价格指纹强制客户确认变价；下单复用既有销售订单流程。
 */
@Service
@RequiredArgsConstructor
public class MallCheckoutService {

    private final MallCatalogService catalogService;
    private final MallCatalogMapper catalog;
    private final CustomerPriceResolver pricing;
    private final MallAddressService addressService;
    private final MallCartService cart;
    private final MallOrderAddressMapper orderAddresses;
    private final SalesOrderApplicationService orders;
    private final MallCheckoutFingerprint fingerprints;

    public MallCheckoutPreviewResponse preview(long customerId, MallCheckoutPreviewRequest request) {
        Priced priced = price(customerId, request.items());
        MallAddressResponse address = addressService.requireAddress(customerId, request.addressId());
        return new MallCheckoutPreviewResponse(priced.items(), SalesOrderConverter.decimal(priced.totalQuantity()),
                SalesOrderConverter.decimal(priced.totalAmount()),
                fingerprints.create(customerId, request.addressId(), priced.items()), address, 0);
    }

    @Transactional
    public MallOrderSubmitResponse submit(long customerId, MallOrderSubmitRequest request, String idempotencyKey) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(MallErrorCodes.ORDER_ITEMS_REQUIRED);
        }
        Priced priced = price(customerId, request.items());
        String expected = fingerprints.create(customerId, request.addressId(), priced.items());
        if (!expected.equals(request.priceFingerprint() == null ? "" : request.priceFingerprint().trim())) {
            throw new BusinessException(MallErrorCodes.PRICE_CHANGED);
        }
        MallAddressResponse address = addressService.requireAddress(customerId, request.addressId());

        List<SalesOrderItemSaveRequest> items = new ArrayList<>();
        for (MallCheckoutItemRequest item : request.items()) {
            items.add(new SalesOrderItemSaveRequest(null, null, item.skuId(), item.quantity(), null, false, null));
        }
        SalesOrderSaveRequest save = new SalesOrderSaveRequest(0, customerId, OrderSource.MALL, null, null, items);
        long orderId = orders.create(save, idempotencyKey);
        SalesOrderResponse submitted = orders.submit(orderId, 0, idempotencyKey);
        saveAddressSnapshot(customerId, orderId, address);
        cart.removeSubmitted(customerId, request.items().stream().map(MallCheckoutItemRequest::skuId).toList());
        return new MallOrderSubmitResponse(submitted.id(), submitted.orderNo(), submitted.status(),
                submitted.totalAmount());
    }

    private void saveAddressSnapshot(long customerId, long orderId, MallAddressResponse address) {
        MallOrderAddressEntity snapshot = new MallOrderAddressEntity();
        snapshot.setOrderId(orderId);
        snapshot.setCustomerId(customerId);
        snapshot.setReceiverName(address.receiverName());
        snapshot.setPhone(address.phone());
        snapshot.setRegion(address.region());
        snapshot.setDetailAddress(address.detailAddress());
        snapshot.setCreatedBy("MALL");
        orderAddresses.insert(snapshot);
    }

    private Priced price(long customerId, List<MallCheckoutItemRequest> requests) {
        Set<Long> seen = new HashSet<>();
        for (MallCheckoutItemRequest item : requests) {
            if (!seen.add(item.skuId())) {
                throw new BusinessException(com.xianshuyuan.scm.order.service.OrderErrorCodes.DUPLICATE_SKU);
            }
        }
        String policy = catalogService.visibilityPolicy(customerId);
        List<MallProductRow> rows = new ArrayList<>();
        for (MallCheckoutItemRequest item : requests) {
            MallProductRow row = catalog.selectProduct(customerId, item.skuId(), policy);
            if (row == null) {
                throw new BusinessException(MallErrorCodes.SKU_NOT_VISIBLE);
            }
            rows.add(row);
        }
        Map<Long, ResolvedCustomerPrice> prices = pricing.resolve(customerId,
                        rows.stream().map(MallProductRow::getSkuId).toList(), OffsetDateTime.now()).stream()
                .collect(Collectors.toMap(ResolvedCustomerPrice::skuId, Function.identity(), (a, b) -> a));

        List<MallCheckoutItemResponse> items = new ArrayList<>();
        BigDecimal totalQuantity = BigDecimal.ZERO.setScale(4);
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(4);
        int index = 0;
        for (MallCheckoutItemRequest item : requests) {
            MallProductRow row = rows.get(index++);
            ResolvedCustomerPrice price = prices.get(row.getSkuId());
            BigDecimal quantity = new BigDecimal(item.quantity()).setScale(4);
            BigDecimal lineAmount = OrderAmountCalculator.lineAmount(quantity, price.unitPrice());
            items.add(new MallCheckoutItemResponse(row.getSkuId(), row.getProductName(), row.getSpecName(),
                    row.getSaleUnit(), row.getProductType(), SalesOrderConverter.decimal(quantity),
                    SalesOrderConverter.decimal(price.unitPrice()), price.source(), price.sourceRecordId(),
                    SalesOrderConverter.decimal(lineAmount)));
            totalQuantity = totalQuantity.add(quantity);
            totalAmount = totalAmount.add(lineAmount);
        }
        return new Priced(items, totalQuantity, totalAmount);
    }

    private record Priced(List<MallCheckoutItemResponse> items, BigDecimal totalQuantity, BigDecimal totalAmount) {
    }
}
