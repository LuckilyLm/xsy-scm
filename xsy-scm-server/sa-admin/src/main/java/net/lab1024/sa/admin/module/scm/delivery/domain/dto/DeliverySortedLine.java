package net.lab1024.sa.admin.module.scm.delivery.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 发车取数：一条销售订单行对应的分拣实发事实（内部读模型，不作为接口返回体）。
 *
 * <p>只取发车必需的三个键与实发量。刻意**不取计划量与结果码**：发车只消费
 * {@code sorted_quantity} 这一个事实（P2 裁决第 1 条），把 SHORT / OUT_OF_STOCK 的判定
 * 再抄一份到配送侧，就会出现同一个事实两个权威来源。
 */
@Data
public class DeliverySortedLine {
    private Long orderId;
    private Long salesOrderItemId;
    private Long skuId;
    private BigDecimal sortedQuantity;
}
