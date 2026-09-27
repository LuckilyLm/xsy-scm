package com.xsy.scm.order.support;

/**
 * Legacy command shape retained for compatibility; production order commands use the inventory reservation service.
 */
public interface OrderInventoryContract {
    record Reserve(Long orderId, Long skuId, Long warehouseId, java.math.BigDecimal quantity, String idempotencyKey) {
    }

    record Release(Long orderId, String idempotencyKey) {
    }

    record Availability(java.math.BigDecimal available, java.math.BigDecimal reserved) {
    }

    void reserve(Reserve command);

    void release(Release command);

    Availability queryAvailability(Long skuId, Long warehouseId);
}
