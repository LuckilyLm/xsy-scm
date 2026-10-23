package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 库存健康度判定输入行：只带原始数据（可用量 + 上下限），不带结论。
 *
 * <p>
 * 分档只在 {@code ScmInventoryWarningStatusEnum#evaluate} 实现一次。若 SQL 也判一份，两份规则漂移后首页与大屏就会和库存预警列表对不上。
 */
public record InventoryHealthRow(BigDecimal availableQuantity, BigDecimal warnMin, BigDecimal warnMax) {
}
