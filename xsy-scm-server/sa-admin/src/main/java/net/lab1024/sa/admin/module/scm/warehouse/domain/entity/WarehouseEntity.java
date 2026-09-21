package net.lab1024.sa.admin.module.scm.warehouse.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;

import java.time.OffsetDateTime;

/**
 * 仓库（最小主数据，W5 Target Design Q1 / §5.2）。
 *
 * <p>W5 只维护**一个**启用仓库（G-03 单仓库口径，由种子数据表达），
 * 但**不在 DB 层**加「最多一行」约束 —— 那会让 W6 / 未来多仓扩展必须改约束。
 */
@Data
@TableName(value = "warehouse", autoResultMap = true)
public class WarehouseEntity {
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(nullsUsing = com.fasterxml.jackson.databind.ser.std.NullSerializer.class)
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.math.BigDecimal longitude;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(nullsUsing = com.fasterxml.jackson.databind.ser.std.NullSerializer.class)
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private java.math.BigDecimal latitude;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
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
