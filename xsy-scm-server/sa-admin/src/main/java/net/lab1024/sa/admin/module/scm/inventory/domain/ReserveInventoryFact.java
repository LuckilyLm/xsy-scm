package net.lab1024.sa.admin.module.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 预留事实（库存域入参记录）。
 *
 * <p>本波次的来源是**销售订单行**（{@code sourceDocumentType = SALES_ORDER_ITEM}，
 * {@code sourceDocumentItemId = sales_order_item.id}）。
 * 用「来源单据类型 + 行 id」而不是直接写死 {@code salesOrderItemId}，
 * 是为了让未来「预留挂在别的单据上」时不必改表结构与防重索引。
 *
 * @param warehouseId          预留仓库
 * @param skuId                预留 SKU
 * @param sourceDocumentType   {@code ScmInventorySourceDocumentTypeEnum}
 * @param sourceDocumentId     来源单据头 id（头级溯源，不参与防重）
 * @param sourceDocumentItemId 来源单据行 id（防重锚点）
 * @param quantity             预留数量，必须为正
 * @param occurredAt           发生时刻 —— 取来源单据的确认时刻，不是写入时刻
 * @param operator             操作者；为空时由服务层取当前登录人
 */
public record ReserveInventoryFact(
        Long warehouseId,
        Long skuId,
        String sourceDocumentType,
        Long sourceDocumentId,
        Long sourceDocumentItemId,
        BigDecimal quantity,
        OffsetDateTime occurredAt,
        String operator) {
}
