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
    /**
     * 商城下单时选定的配送仓；后台及历史订单为空。
     */
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

    /**
     * 商城客户下单时预选的优惠券；仅 {@code orderSource = MALL} 使用，确认时才正式占用。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long mallCouponInstanceId;

    /**
     * 商城结算约定（{@code WALLET} / {@code CREDIT} / {@code COD}）。历史订单沿用原约定，新仅下单订单由后台确认时确定。
     * 它只是结算约定，不代表资金已处理：余额扣减、账期占用、货到付款收款分别由对应模块在各自节点完成。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String settlementMethod;
    /** 创建时由商城服务决定，不允许编辑订单时改变历史业务模式。 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Boolean mallOrderOnly = false;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime submittedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime confirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime cancelledAt;
    /** 最近一次发车出库时间；由此起算 7 天自动锁单。 */
    private OffsetDateTime dispatchedAt;
    /** 锁单标记：锁定后普通改单被拒绝，只有持有锁后改单权限的账号能继续修改。 */
    private Boolean lockedFlag = false;
    private OffsetDateTime lockedAt;
    private String lockedBy;
    /** MANUAL 手动锁单；AUTO_7_DAYS 发车满 7 天自动锁单。 */
    private String lockReason;
    private OffsetDateTime autoLockAt;
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
