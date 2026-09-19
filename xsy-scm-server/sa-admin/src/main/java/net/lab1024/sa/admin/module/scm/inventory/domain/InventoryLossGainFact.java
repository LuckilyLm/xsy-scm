package net.lab1024.sa.admin.module.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 报损报溢事实（库存域自己的入参记录）。
 *
 * <p>与 {@link InventoryStocktakeFact} / {@link InventoryOutboundFact} 同一取向：
 * 调用方是库存域自己的单据服务，同域直接传记录即可。
 *
 * <p><b>方向不在这里</b>：{@code adjustType} 是单据头的属性，命令服务按它选流水类型与加减。
 * 让调用方传一个「入/出」标志等于把方向的定义权分散到调用方，
 * 而方向必须与 {@code movement_type} 一一对应（{@code ck_inventory_movement_snap} 依赖它）。
 *
 * @param warehouseId    仓库
 * @param skuId          SKU
 * @param lossGainId     报损报溢单 id（头级溯源，不参与防重）
 * @param lossGainItemId 报损报溢单行 id（防重锚点，唯一索引列）
 * @param adjustType     {@code ScmInventoryLossGainTypeEnum} 的持久化值（LOSS / OVERFLOW）
 * @param quantity       申报数量，必须为正（方向由 adjustType 表达，数量恒正）
 * @param occurredAt     发生时刻 —— 取**审核时刻**，不是创建时刻、不是写入时刻
 * @param operator       操作者 —— 取**审核人**，不是创建人
 */
public record InventoryLossGainFact(
        Long warehouseId,
        Long skuId,
        Long lossGainId,
        Long lossGainItemId,
        String adjustType,
        BigDecimal quantity,
        OffsetDateTime occurredAt,
        String operator) {
}
