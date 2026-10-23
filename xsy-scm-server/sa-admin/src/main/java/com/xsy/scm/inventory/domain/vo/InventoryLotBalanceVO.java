package com.xsy.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 批次库存行。
 *
 * <p>
 * 展示字段与余额页同口径：编码/名称取主数据当前值，只有数量与单位来自批次子账。数量用 4 位定点字符串协议，前端不参与计算。
 */
@Data
public class InventoryLotBalanceVO {

    private Long id;

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private Long lotId;

    /**
     * 内部批次号：系统发号，只读展示。
     */
    private String lotNo;

    /**
     * 来源生产批号：供应商或现场提供，可能为空。
     */
    private String sourceLotNo;

    private String unit;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    private OffsetDateTime updatedAt;
}
