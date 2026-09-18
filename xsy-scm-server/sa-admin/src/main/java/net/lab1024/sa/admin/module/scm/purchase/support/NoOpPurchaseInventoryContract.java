package net.lab1024.sa.admin.module.scm.purchase.support;

/**
 * W5 阶段的契约占位实现 —— **W6 起不再被使用，也从未注册为 Bean**。
 *
 * <p>历史事实（W5 Target Design §8.1 / §8.5）：W5 不注册任何库存契约 Bean，
 * 因此不存在「看起来在写库存但其实什么都没发生」的假象；该边界当时由
 * {@code PurchaseInventoryContractAbsenceIT} 断言。
 *
 * <p><b>W6 现状</b>：真实实现是
 * {@code net.lab1024.sa.admin.module.scm.inventory.support.PurchaseInventoryContractImpl}
 * （唯一注册的 Bean），调用点是 {@code PurchaseReceiptService.confirm} 的同一事务内。
 * {@code PurchaseInventoryContractAbsenceIT} 已按 Q6 废止，容器断言由 W6 的
 * {@code ScmInventoryMigrationIT}（Bean 恰好 1 个）接替。
 *
 * <p><b>为什么不删除本类</b>：它是 purchase 模块的既有资产，删除会让 W6 的 diff 触及
 * 与库存无关的文件；保留它（且继续不注册）保持了「purchase 侧零改动」这一可审计事实。
 * 若将来确认无人引用，可在一次纯清理提交中删除。
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
