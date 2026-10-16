package com.xsy.scm.purchase.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

/** 收货称重记录只追加，保留设备读数、人工接受结果和采集时间。历史记录不得修改或软删，纠错需要另记事实。 */
@Data
@TableName(value = "xsy_receipt_weighing_record", autoResultMap = true)
public class ReceiptWeighingRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long purchaseReceiptItemId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal rawReading;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal confirmedReading;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String source;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String deviceSessionId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String modificationReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime recordedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operator;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;
}
