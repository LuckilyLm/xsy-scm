package com.xsy.scm.inventory.domain.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 批次余额子账。
 *
 * <p>
 * 与总账 {@code xsy_inventory_balance} 并存：总账仍按仓库 + 商品规格唯一，本表按仓库 + 商品规格 + 批次唯一。
 * 不变量是「同仓同商品同单位下各批次之和等于总账」，由命令在同事务内双写保证，查询层不做二次计算。
 */
@Data
public class InventoryLotBalanceEntity {

    private Long id;

    private Long warehouseId;

    private Long skuId;

    private Long lotId;

    private String unit;

    private BigDecimal quantity;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}
