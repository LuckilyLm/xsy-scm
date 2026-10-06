package com.xsy.scm.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 库存流水，append-only 账本。
 *
 * <p>
 * 表上有 {@code deleted} 列（为与部分唯一索引 {@code WHERE deleted = FALSE AND source_document_item_id IS NOT NULL} 逐字匹配），但
 * {@code ck_inventory_movement_append_only CHECK (deleted = FALSE)} 把它锁死为 FALSE：soft delete / update 历史流水在数据库层直接失败。
 * 因此本实体没有 {@code version} / {@code updatedAt} / {@code updatedBy}，对应 DAO 只有 insert + select；未来冲销（如采购退货）必须新增反向
 * movement，不得修改历史行。
 *
 * <p>
 * 不用 {@code @TableLogic}（它表达「可被软删的实体」，与本表语义相反）：所有读取在 SQL 里显式写 {@code deleted = FALSE}，与部分唯一索引谓词同口径。
 *
 * <p>
 * 溯源三件套：{@code sourceDocumentType + sourceDocumentItemId} 是防重锚点，{@code sourceDocumentId}
 * 是头级溯源（列表页跳转收货单用，不参与唯一索引）。人类可读的来源单号由查询侧联 {@code purchase_receipt.receipt_no} 取得。
 */
@Data
@TableName(value = "inventory_movement", autoResultMap = true)
public class InventoryMovementEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    private Long skuId;

    /**
     * {@code ScmInventoryMovementTypeEnum}；仅 {@code PURCHASE_IN}（DB CHECK 白名单）。
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
     * 采购成本事实快照；可空只为未来的无成本 movement 类型预留表达空间。
     */
    private BigDecimal unitCost;

    private BigDecimal beforeQuantity;

    private BigDecimal afterQuantity;

    /**
     * 发生时刻 —— <b>等于 {@code purchase_receipt.confirmed_at}</b>，不是写入时刻。
     *
     * <p>
     * 实时路径与 backfill 路径同口径：都由收货确认事实传入，禁止 {@code now()} 替代。
     */
    private OffsetDateTime occurredAt;

    /**
     * 操作者 —— <b>等于 {@code purchase_receipt.operator}</b>（已冻结的收货确认事实）。
     */
    private String operator;

    /**
     * 恒为 FALSE（{@code ck_inventory_movement_append_only}），保留列只为匹配契约索引谓词。
     */
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private String createdBy;
}
