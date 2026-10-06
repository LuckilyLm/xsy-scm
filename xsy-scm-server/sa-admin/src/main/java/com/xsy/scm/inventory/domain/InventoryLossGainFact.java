package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 报损报溢事实。
 *
 * <p>
 * <b>方向不在这里</b>：{@code adjustType} 是单据头的属性，命令服务按它选流水类型与加减。让调用方传「入 / 出」标志等于把方向的定义权分散出去，而方向必须与 {@code movement_type}
 * 一一对应（{@code ck_inventory_movement_snap} 依赖它）。数量恒为正，方向由 {@code adjustType} 表达。
 *
 * <p>
 * {@code occurredAt} 取<b>审核时刻</b>，{@code operator} 取<b>审核人</b>，不是创建时刻 / 创建人。
 */
public record InventoryLossGainFact(Long warehouseId, Long skuId, Long lossGainId, Long lossGainItemId,
        String adjustType, BigDecimal quantity, OffsetDateTime occurredAt, String operator) {
}
