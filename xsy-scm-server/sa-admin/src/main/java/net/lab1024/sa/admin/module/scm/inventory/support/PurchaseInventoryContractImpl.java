package net.lab1024.sa.admin.module.scm.inventory.support;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryCommandService;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import org.springframework.stereotype.Component;

/**
 * 库存契约实现（W6 Target Design §6.1）—— 容器中**唯一**的
 * {@link PurchaseInventoryContract} Bean。
 *
 * <p>本类刻意只做「转发 + 语义翻译」，不承载任何领域逻辑：真正的写路径在
 * {@link InventoryCommandService}。这样做的理由是可测试性 —— IT 可以直接调用
 * {@code InventoryCommandService} 而不必绕过 Spring 装配，同时容器里仍然只有一个
 * 契约 Bean（由 W6 IT 断言）。
 *
 * <p><b>不另开事务</b>：{@link #postInbound} 必须加入调用方（收货确认）的事务，
 * 这是「库存与收货同事务」的实现形态。见 {@link InventoryCommandService} 的类注释。
 */
@Component
@RequiredArgsConstructor
public class PurchaseInventoryContractImpl implements PurchaseInventoryContract {

    private final InventoryCommandService inventoryCommandService;

    @Override
    public void postInbound(InboundFact fact) {
        inventoryCommandService.postPurchaseInbound(fact);
    }

    @Override
    public Availability queryAvailability(Long skuId, Long warehouseId) {
        return inventoryCommandService.queryAvailability(skuId, warehouseId);
    }
}
