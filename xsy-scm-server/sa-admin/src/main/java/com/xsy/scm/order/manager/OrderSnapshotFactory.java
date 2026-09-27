package com.xsy.scm.order.manager;

import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;

import com.xsy.scm.order.domain.form.SalesOrderItemForm;

import com.xsy.scm.order.constant.ScmOrderPriceSourceEnum;
import com.xsy.scm.common.exception.ScmBusinessException;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_NOT_FOUND;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.customer.domain.entity.CustomerEntity;

public final class OrderSnapshotFactory {
    private OrderSnapshotFactory() {
    }

    public static SalesOrderItemEntity item(SalesOrderItemForm salesOrderItemForm,
        ProductSkuOptionVO productSku, String spuCode, ResolvedPriceVO resolvedPrice) {
        if (productSku == null) throw new ScmBusinessException(ORDER_ITEM_NOT_FOUND);
        var orderItem = new SalesOrderItemEntity();
        orderItem.setId(salesOrderItemForm.getItemId());
        orderItem.setVersion(salesOrderItemForm.getVersion());
        orderItem.setSkuId(productSku.getSkuId());
        orderItem.setSpuId(productSku.getSpuId());
        orderItem.setSpuCodeSnapshot(spuCode);
        orderItem.setProductNameSnapshot(productSku.getProductName());
        orderItem.setSkuCodeSnapshot(productSku.getSkuCode());
        orderItem.setSpecNameSnapshot(productSku.getSpecName());
        orderItem.setSpecValuesSnapshot(
                new LinkedHashMap<>(
                        productSku.getSpecValues() == null ? Map.of() : productSku.getSpecValues()));
        orderItem.setSaleUnitSnapshot(productSku.getSaleUnit());
        orderItem.setProductTypeSnapshot(productSku.getProductType());
        orderItem.setOrderedQuantity(OrderValidator.decimal(salesOrderItemForm.getOrderedQuantity(), true));
        orderItem.setManualPriceOverride(Boolean.TRUE.equals(salesOrderItemForm.getManualPriceOverride()));
        orderItem.setManualPriceReason(
                orderItem.getManualPriceOverride()
                        ? OrderValidator.trim(salesOrderItemForm.getOverrideReason())
                        : null);
        if (orderItem.getManualPriceOverride()) {
            orderItem.setDraftUnitPrice(OrderValidator.decimal(salesOrderItemForm.getUnitPrice(), false));
            orderItem.setDraftPriceSource(ScmOrderPriceSourceEnum.OVERRIDE.name());
        } else applyPrice(orderItem, resolvedPrice);
        orderItem.setOrderedLineAmount(OrderAmountCalculator.lineAmount(orderItem.getOrderedQuantity(),
            orderItem.getDraftUnitPrice()));
        orderItem.setSortOrder(salesOrderItemForm.getSortOrder() == null ? 0 : salesOrderItemForm.getSortOrder());
        return orderItem;
    }

    public static void applyPrice(SalesOrderItemEntity orderItem, ResolvedPriceVO resolvedPrice) {
        orderItem.setDraftUnitPrice(resolvedPrice.getUnitPrice());
        orderItem.setDraftPriceSource(
                resolvedPrice.getPriceSource() == null ? null : resolvedPrice.getPriceSource().name());
        orderItem.setDraftPriceSourceId(resolvedPrice.getSourceRecordId());
    }

    public static void customer(SalesOrderEntity salesOrder, CustomerEntity customer) {
        salesOrder.setCustomerId(customer.getId());
        salesOrder.setCustomerCodeSnapshot(customer.getCustomerCode());
        salesOrder.setCustomerNameSnapshot(customer.getName());
        salesOrder.setSettleModeSnapshot(customer.getSettleMode());
        salesOrder.setSellerId(customer.getSellerId());
    }
}
