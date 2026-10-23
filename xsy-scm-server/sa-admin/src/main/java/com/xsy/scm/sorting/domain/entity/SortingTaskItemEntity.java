package com.xsy.scm.sorting.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 每条分拣明细记录订单行的计划量、实拣量和结果。实拣量与结果必须同时填写；分拣不直接修改订单数量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xsy_sorting_task_item")
public class SortingTaskItemEntity extends SortingRecord {
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long taskId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOrderItemId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNoSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long spuId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String spuCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String productNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String skuCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String specNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String saleUnitSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String productTypeSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal plannedQuantitySnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal sortedQuantity;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String result;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;
    private String sortedBy;
    private OffsetDateTime sortedAt;
    /** {@code ACTIVE} 占用该订单行；任务取消时同一事务内置 {@code RELEASED}，行保留作历史。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String occupationStatus;
}
