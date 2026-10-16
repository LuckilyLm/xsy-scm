package com.xsy.scm.order.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
@TableName(value = "xsy_sales_order", autoResultMap = true)
public class SalesOrderEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderSource;
    /** 商城下单时冻结的配送仓；后台及历史订单为空。 */
    private Long mallDeliveryWarehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long originalOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplementReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedTotalAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal settlementTotalAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String settleModeSnapshot;
    private Long settlementCustomerId;
    private String settlementCustomerNameSnapshot;
    private String creditOverrideReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime expectDeliveryTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sellerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cancelReason;

    /** 商城客户下单时预选的券实例；仅 {@code orderSource = MALL} 使用，正式占用在确认命令内完成。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long mallCouponInstanceId;

    /**
     * 商城客户选定的结算方式（{@code WALLET} / {@code CREDIT} / {@code COD}）；仅商城来源的订单会带。
     * 它是「结算约定」而不是资金事实：余额扣减、账期占用、货到付款收款分别由对应领域在各自节点完成。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String settlementMethod;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime submittedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime confirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime cancelledAt;
    @Version
    private Integer version = 0;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime updatedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String updatedBy;
}
