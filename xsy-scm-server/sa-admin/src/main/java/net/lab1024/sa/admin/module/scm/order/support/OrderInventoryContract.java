package net.lab1024.sa.admin.module.scm.order.support;


/**
 * Future integration only: W4 neither implements nor calls inventory mutations.
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
