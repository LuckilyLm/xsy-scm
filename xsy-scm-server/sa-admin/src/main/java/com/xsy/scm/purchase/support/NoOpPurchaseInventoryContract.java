package com.xsy.scm.purchase.support;

/**
 * 已停用的占位实现，用来显式说明没有可用的库存 bean。不要注册到 Spring；生产环境由库存模块提供实现。
 */
public final class NoOpPurchaseInventoryContract implements PurchaseInventoryContract {

    @Override
    public void postInbound(InboundFact fact) {
        // 有意留空
    }

    @Override
    public Availability queryAvailability(Long skuId, Long warehouseId) {
        // null 表示「库存未启用」，不是「可用量为零」
        return null;
    }
}
