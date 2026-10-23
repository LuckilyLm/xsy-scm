package com.xsy.scm.order.support;

/**
 * 兼容用的空实现；订单命令现在直接使用库存预留服务。
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
