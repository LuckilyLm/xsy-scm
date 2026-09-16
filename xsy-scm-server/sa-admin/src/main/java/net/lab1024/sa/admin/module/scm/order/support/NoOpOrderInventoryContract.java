package net.lab1024.sa.admin.module.scm.order.support;

/** Contract placeholder only. No W4 order command invokes these methods. */
public final class NoOpOrderInventoryContract implements OrderInventoryContract {
    @Override public void reserve(Reserve command) { }
    @Override public void release(Release command) { }
    @Override public Availability queryAvailability(Long skuId,Long warehouseId) {return null;}
}
