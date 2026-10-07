package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 库存健康度判定输入行：只带事实（可用量 + 上下限），不带结论。
 *
 * <p>
 * 分档只有 {@code ScmInventoryWarningStatusEnum#evaluate} 一处实现。若让 SQL 也判一份「正常 / 低于下限 / 高于上限」， 两份规则漂移后首页与大屏的健康度就会和库存预警列表对不上。
 */
public record InventoryHealthRow(BigDecimal availableQuantity, BigDecimal warnMin, BigDecimal warnMax) {
}
