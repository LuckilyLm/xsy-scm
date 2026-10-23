package com.xsy.scm.order.manager;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import com.xsy.scm.inventory.service.InventoryReservationService;
import com.xsy.scm.inventory.service.InventorySaleUnitConverter;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_STATE_INVALID;

/**
 * 只有显式整单预留命令才消费订单确认时冻结的组合组成数量。
 */
public final class OrderBundleReservationPlanner {

    private OrderBundleReservationPlanner() {
    }

    public static List<InventoryReservationService.OrderReserveLine> lines(List<OrderBundleComponentEntity> components,
        List<SalesOrderItemEntity> orderItems) {
        if (components == null || components.isEmpty()) {
            return List.of();
        }
        Map<Long, SalesOrderItemEntity> parents = orderItems.stream()
            .collect(Collectors.toMap(SalesOrderItemEntity::getId, Function.identity()));
        List<InventoryReservationService.OrderReserveLine> lines = new ArrayList<>(components.size());
        for (OrderBundleComponentEntity component : components) {
            SalesOrderItemEntity parent = parents.get(component.getOrderItemId());
            BigDecimal frozenQty = component.getComponentSaleQuantitySnapshot();
            if (parent == null || parent.getActualQuantity() == null || component.getQuantityPerBundleSaleUnit() == null
                || frozenQty == null || frozenQty.signum() <= 0 || frozenQty
                    .compareTo(parent.getActualQuantity().multiply(component.getQuantityPerBundleSaleUnit())) != 0) {
                throw new ScmBusinessException(ORDER_STATE_INVALID);
            }
            InventorySaleUnitConverter.toStockQuantity(frozenQty, component.getComponentStockUnitSnapshot(),
                component.getStockUnitsPerComponentSaleUnit());
            lines.add(new InventoryReservationService.OrderReserveLine(component.getId(), component.getComponentSkuId(),
                frozenQty, component.getComponentStockUnitSnapshot(), component.getStockUnitsPerComponentSaleUnit(),
                ScmInventorySourceDocumentTypeEnum.ORDER_BUNDLE_COMPONENT.name()));
        }
        return List.copyOf(lines);
    }
}
