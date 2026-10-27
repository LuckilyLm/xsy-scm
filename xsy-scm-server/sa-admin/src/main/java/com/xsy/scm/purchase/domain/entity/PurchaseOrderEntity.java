package com.xsy.scm.purchase.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

/**
 * 采购单从草稿提交，允许部分收货、全部收货或少收关单；草稿和已提交单可取消。正式履约及取消时点随状态一同保存。
 */
@Data
@TableName(value = "xsy_purchase_order", autoResultMap = true)
public class PurchaseOrderEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaserId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String warehouseCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String warehouseNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate plannedArrivalDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal totalAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cancelReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shortCloseReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime submittedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime cancelledAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime shortClosedAt;
    /** 锁单开关：只关掉补价/改价入口，不改变采购状态，收货与应付照常。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Boolean lockedFlag;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime lockedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lockedBy;
    /** MANUAL 手动锁单 / AUTO_7_DAYS 提交满 7 天自动锁单。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lockReason;
    /** 自动锁单时点 = 提交时刻 + 7 天；草稿单为空。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
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
