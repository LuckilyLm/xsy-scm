package com.xsy.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 批次流水行。
 *
 * <p>
 * {@code quantity} 是有符号数量：入库为正、出库为负，{@code beforeQuantity + quantity = afterQuantity}。
 * 与总账流水的区别：一条总账流水可能按批次拆成多条子账流水，子账之和等于总账数量。
 */
@Data
public class InventoryLotMovementVO {

    private Long id;

    private Long warehouseId;

    private String warehouseName;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private Long lotId;

    private String lotNo;

    private String movementType;

    private String sourceDocumentType;

    private Long sourceDocumentId;

    private Long sourceDocumentItemId;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    private String unitSnapshot;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal beforeQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal afterQuantity;

    private OffsetDateTime occurredAt;

    private String operator;
}
