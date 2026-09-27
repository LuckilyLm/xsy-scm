package com.xsy.scm.supplier.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 供应商（聚合根）。
 *
 * <p>
 * 可空联系人、电话和地址使用 {@code FieldStrategy.ALWAYS}，以便编辑时通过 {@code null} 清空字段；MyBatis-Plus 默认 {@code NOT_NULL} 会忽略这些更新。
 */
@Data
@TableName(value = "supplier", autoResultMap = true)
public class SupplierEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;

    private String supplierCode;

    private String name;

    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactPhone;

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
}
