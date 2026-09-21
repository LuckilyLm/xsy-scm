package net.lab1024.sa.admin.module.scm.order.manager;

import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;

import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;

import java.math.BigDecimal;
import java.util.*;

import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionVO;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.ResolvedPriceVO;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerEntity;

public final class OrderSnapshotFactory {
    private OrderSnapshotFactory() {
    }

    public static SalesOrderItemEntity item(SalesOrderItemForm f, ProductSkuOptionVO sku, String spuCode, ResolvedPriceVO price) {
        if (sku == null) throw new ScmBusinessException(ORDER_ITEM_NOT_FOUND);
        var x = new SalesOrderItemEntity();
        x.setId(f.getItemId());
        x.setVersion(f.getVersion());
        x.setSkuId(sku.getSkuId());
        x.setSpuId(sku.getSpuId());
        x.setSpuCodeSnapshot(spuCode);
        x.setProductNameSnapshot(sku.getProductName());
        x.setSkuCodeSnapshot(sku.getSkuCode());
        x.setSpecNameSnapshot(sku.getSpecName());
        x.setSpecValuesSnapshot(new LinkedHashMap<>(sku.getSpecValues() == null ? Map.of() : sku.getSpecValues()));
        x.setSaleUnitSnapshot(sku.getSaleUnit());
        x.setProductTypeSnapshot(sku.getProductType());
        x.setOrderedQuantity(OrderValidator.decimal(f.getOrderedQuantity(), true));
        x.setManualPriceOverride(Boolean.TRUE.equals(f.getManualPriceOverride()));
        x.setManualPriceReason(x.getManualPriceOverride() ? OrderValidator.trim(f.getOverrideReason()) : null);
        if (x.getManualPriceOverride()) {
            x.setDraftUnitPrice(OrderValidator.decimal(f.getUnitPrice(), false));
            x.setDraftPriceSource("OVERRIDE");
        } else applyPrice(x, price);
        x.setOrderedLineAmount(OrderAmountCalculator.lineAmount(x.getOrderedQuantity(), x.getDraftUnitPrice()));
        x.setSortOrder(f.getSortOrder() == null ? 0 : f.getSortOrder());
        return x;
    }

    public static void applyPrice(SalesOrderItemEntity x, ResolvedPriceVO price) {
        x.setDraftUnitPrice(price.getUnitPrice());
        x.setDraftPriceSource(price.getPriceSource() == null ? null : price.getPriceSource().name());
        x.setDraftPriceSourceId(price.getSourceRecordId());
    }

    public static void customer(SalesOrderEntity o, CustomerEntity customer) {
        o.setCustomerId(customer.getId());
        o.setCustomerCodeSnapshot(customer.getCustomerCode());
        o.setCustomerNameSnapshot(customer.getName());
        o.setSettleModeSnapshot(customer.getSettleMode());
        o.setSellerId(customer.getSellerId());
    }
}
