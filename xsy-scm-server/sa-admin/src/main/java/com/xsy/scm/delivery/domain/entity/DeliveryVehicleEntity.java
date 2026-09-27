package com.xsy.scm.delivery.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "delivery_vehicle", autoResultMap = true)
public class DeliveryVehicleEntity extends DeliveryRecord {
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String vehicleNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String vehicleType;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal loadWeight;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal loadVolume;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
