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
 * 客户联系人、地址和账期直接保存在客户记录中。可选字段允许保存 null，以支持在编辑时清空旧值。
 */
@Data
@TableName(value = "xsy_customer", autoResultMap = true)
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
     * 集团主体是否允许子客户使用集团钱包结算；默认关闭。
     */
    private Boolean sharedSettlementEnabled;

    /**
     * 后台明确开通后，客户才可在商城选择货到付款。
     */
    private Boolean codAllowed = false;
    /**
     * 商城默认配送仓；为空时按地址区县规则匹配。
     */
    private Long mallDeliveryWarehouseId;

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
