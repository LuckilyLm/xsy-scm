package com.xsy.scm.purchase.support;

/**
 * Disabled placeholder retained to make the absence of a no-op inventory bean explicit.
 * This class must not be registered in Spring; the production bean is supplied by the inventory module.
 */
public final class NoOpPurchaseInventoryContract implements PurchaseInventoryContract {

    @Override
    public void postInbound(InboundFact fact) {
        // intentionally empty
    }

    @Override
    public Availability queryAvailability(Long skuId, Long warehouseId) {
        // null == "inventory not enabled", NOT "available quantity is zero"
        return null;
    }
}
