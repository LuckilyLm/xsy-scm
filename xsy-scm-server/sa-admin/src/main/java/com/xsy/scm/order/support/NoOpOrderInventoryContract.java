package com.xsy.scm.order.support;

/**
 * Preserves the inventory command contract while order commands use the inventory reservation service directly.
 */
public final class NoOpOrderInventoryContract implements OrderInventoryContract {
    @Override
    public void reserve(Reserve command) {
    }

    @Override
    public void release(Release command) {
    }

    @Override
    public Availability queryAvailability(Long skuId, Long warehouseId) {
        return null;
    }
}
