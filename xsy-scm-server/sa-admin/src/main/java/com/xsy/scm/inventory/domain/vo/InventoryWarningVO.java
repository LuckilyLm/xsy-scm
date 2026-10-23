package com.xsy.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 库存预警展示现有量、预留量和可用量；按可用量与阈值判断状态。状态不存库；尚无余额记录时按零库存计算，仍可触发低库存预警。
 */
@Data
public class InventoryWarningVO {

    /**
     * 阈值配置 id（跳转到配置页用）。
     */
    private Long thresholdId;

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private Map<String, String> specValues;

    /**
     * 记账单位；没有余额行时为空。
     */
    private String unit;

    /**
     * 现有量；没有余额行时为 0。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    /**
     * 已预留量；没有余额行时为 0。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal reservedQuantity;

    /**
     * 可用量 = 现有量 − 预留量，预警按它判断。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal availableQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal warnMin;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal warnMax;

    /**
     * {@code NORMAL} / {@code LOW} / {@code HIGH}（服务层按可用量算出）。
     */
    private String status;

    /**
     * 状态中文描述（服务层按枚举填充，前端不硬编码字典）。
     */
    private String statusDesc;
}
