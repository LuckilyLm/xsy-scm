package com.xsy.scm.order.support;

/**
 * 为兼容保留的旧命令接口；生产环境的订单命令直接使用库存预留服务。
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
