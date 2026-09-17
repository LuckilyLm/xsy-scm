package net.lab1024.sa.admin.module.scm.purchase.support;

/**
 * Contract placeholder only. No W5 purchase command invokes these methods.
 *
 * <p>**W5 不把它注册成 Bean**：一旦注册，就会诱导调用方注入并调用它，从而在 W5 阶段
 * 制造「看起来在写库存但其实什么都没发生」的假象。W5 的正确状态是
 * **零库存表 · 零库存写入 · 契约零调用点**（W5 Target Design §8.1 / §8.5），
 * 由 {@code PurchaseInventoryContractAbsenceIT} 断言。
 *
 * <p>W6 接入时：实现本接口为真实 Bean（例如 {@code @ConditionalOnProperty} 或直接替换），
 * 并在 {@code PurchaseReceiptService.confirm} 的同一事务内调用 {@link #postInbound}。
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
