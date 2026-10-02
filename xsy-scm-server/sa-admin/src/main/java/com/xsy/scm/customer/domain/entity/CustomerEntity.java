package com.xsy.scm.customer.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 客户（聚合根）。
 *
 * <p>
 * 联系人 / 地址 / 账期都是内嵌标量列，不建子表。可空列全部显式声明 {@code FieldStrategy.ALWAYS}：MyBatis-Plus 默认 {@code NOT_NULL} 会让 {@code null} 被
 * 静默忽略，导致「清空联系人 / 地址 / 备注」永远失败（风险 R7）。
 */
@Data
@TableName(value = "customer", autoResultMap = true)
public class CustomerEntity {
    @JsonSerialize(nullsUsing = NullSerializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal longitude;
    @JsonSerialize(nullsUsing = NullSerializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal latitude;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String geomCrs;

    private String visibilityPolicy;

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

    private String customerCode;

    private String name;

    private Long customerTypeId;

    private String status;

    private String settleMode;

    /**
     * 授信额度，{@code NUMERIC(18,4)}，非空、默认 0。
     */
    private BigDecimal creditLimit;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentCustomerId;

    private Long settlementCustomerId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sellerId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierId;

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
    private String creditPeriodType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal creditAmountThreshold;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer creditPeriodValue;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String creditPeriodUnit;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer settleDay;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
