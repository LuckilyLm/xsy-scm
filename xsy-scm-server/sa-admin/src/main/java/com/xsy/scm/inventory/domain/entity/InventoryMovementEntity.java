package com.xsy.scm.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 库存流水只能新增，不修改历史。数量和方向分别记录，同一业务来源不可重复记账。
 */
@Data
@TableName(value = "xsy_inventory_movement", autoResultMap = true)
public class InventoryMovementEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    private Long skuId;

    /**
     * {@code ScmInventoryMovementTypeEnum}；仅 {@code PURCHASE_IN}（数据库 CHECK 约束）。
     */
    private String movementType;

    /**
     * {@code ScmInventorySourceDocumentTypeEnum}；仅 {@code PURCHASE_RECEIPT_ITEM}。
     */
    private String sourceDocumentType;

    /**
     * 收货单 id（头级溯源，不参与防重）。
     */
    private Long sourceDocumentId;

    /**
     * 收货行 id（防重锚点，唯一索引列）。
     */
    private Long sourceDocumentItemId;

    /**
     * 恒为正（{@code ck_inventory_movement_qty}）。
     */
    private BigDecimal quantity;

    /**
     * 确认时刻的采购单位快照（{@code purchase_unit_snapshot}）。
     */
    private String unitSnapshot;

    /**
     * 采购成本；可空，只为未来无成本流水类型预留。
     */
    private BigDecimal unitCost;

    private BigDecimal beforeQuantity;

    private BigDecimal afterQuantity;

    /**
     * 发生时刻：等于 {@code xsy_purchase_receipt.confirmed_at}，不是写入时刻。
     *
     * <p>
     * 实时路径与 backfill 路径规则相同：都由收货确认记录传入，禁止用 {@code now()} 替代。
     */
    private OffsetDateTime occurredAt;

    /**
     * 操作者：等于 {@code xsy_purchase_receipt.operator}（已保存的收货确认人）。
     */
    private String operator;

    /**
     * 恒为 FALSE（{@code ck_inventory_movement_append_only}），保留列只为匹配索引条件。
     */
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private String createdBy;
}
