package com.xsy.scm.inventory.support;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.service.InventoryCommandService;
import com.xsy.scm.purchase.support.PurchaseInventoryContract;
import org.springframework.stereotype.Component;

/** 本类为采购收货调用库存命令的适配层，只传递已确认的收货事实并转换异常；库存扣加和流水写入仍由库存域统一执行。 */
@Component
@RequiredArgsConstructor
public class PurchaseInventoryContractImpl implements PurchaseInventoryContract {

    private final InventoryCommandService inventoryCommandService;

    @Override
    public void postInbound(InboundFact fact) {
        inventoryCommandService.postPurchaseInbound(fact);
    }

    @Override
    public Availability queryAvailability(Long skuId, Long warehouseId) {
        return inventoryCommandService.queryAvailability(skuId, warehouseId);
    }
}
