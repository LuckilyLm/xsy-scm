package com.xsy.scm.warehouse.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 仓库主数据。
 *
 * <p>
 * 订单没有仓库字段，预留库存时必须能解析出唯一启用仓库；数据库不限制仓库记录总数。
 */
@Data
@TableName(value = "warehouse", autoResultMap = true)
public class WarehouseEntity {
    @JsonSerialize(nullsUsing = NullSerializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal longitude;
    @JsonSerialize(nullsUsing = NullSerializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal latitude;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String geomCrs;

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String warehouseCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;
    /**
     * 省 / 市 / 区编码为国标六位码，名称是同一条选择的快照，展示与导出用，不参与关联。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer provinceCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String provinceName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer cityCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cityName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer districtCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String districtName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
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
