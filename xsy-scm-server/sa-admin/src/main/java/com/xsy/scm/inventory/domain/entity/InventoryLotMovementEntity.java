package com.xsy.scm.inventory.domain.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 批次流水：正数入库、负数出库，只追加。
 *
 * <p>
 * 与总账流水分开记：一条总账流水可能按批次拆成多条子账流水，子账数量之和等于总账数量。
 */
@Data
public class InventoryLotMovementEntity {

    private Long id;

    private Long warehouseId;

    private Long skuId;

    private Long lotId;

    private String movementType;

    private String sourceDocumentType;

    private Long sourceDocumentId;

    private Long sourceDocumentItemId;

    /**
     * 有符号数量：入库为正、出库为负。
     */
    private BigDecimal quantity;

    private String unitSnapshot;

    private BigDecimal beforeQuantity;

    private BigDecimal afterQuantity;

    private OffsetDateTime occurredAt;

    private String operator;

    private OffsetDateTime createdAt;

    private String createdBy;
}
