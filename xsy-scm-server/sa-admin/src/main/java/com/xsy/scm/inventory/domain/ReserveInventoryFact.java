package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 预留事实。
 *
 * <p>
 * 预留来源是销售订单行（{@code sourceDocumentType = SALES_ORDER_ITEM}）。用「来源单据类型 + 行 id」 而不是写死
 * {@code salesOrderItemId}，是为了让未来「预留挂在别的单据上」时不必改表结构与防重索引。
 *
 * <p>
 * {@code occurredAt} 取来源单据的确认时刻，不是写入时刻；{@code operator} 为空时由服务层取当前登录人。
 */
public record ReserveInventoryFact(Long warehouseId, Long skuId, String sourceDocumentType, Long sourceDocumentId,
        Long sourceDocumentItemId, BigDecimal quantity, OffsetDateTime occurredAt, String operator) {
}
