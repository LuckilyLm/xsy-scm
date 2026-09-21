package net.lab1024.sa.admin.module.scm.customer.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 客户详情：列表字段 + 地址 / 账期 / 归属关系 / 备注 / 时间戳。
 */
@Data
public class CustomerDetailVO {
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(nullsUsing = com.fasterxml.jackson.databind.ser.std.NullSerializer.class)
    private java.math.BigDecimal longitude;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(nullsUsing = com.fasterxml.jackson.databind.ser.std.NullSerializer.class)
    private java.math.BigDecimal latitude;
    private String geomCrs;

    private String visibilityPolicy;
    private java.util.List<CustomerSkuVisibilityVO> visibilities;


    private Long customerId;

    private Integer version;

    private String customerCode;

    private String name;

    private Long customerTypeId;

    private String customerTypeName;

    private String status;

    private String settleMode;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal creditLimit;

    private String creditPeriodType;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal creditAmountThreshold;

    private Integer creditPeriodValue;

    private String creditPeriodUnit;

    private Integer settleDay;

    private String contactName;

    private String contactPhone;

    private String address;

    private Integer provinceCode;

    private String provinceName;

    private Integer cityCode;

    private String cityName;

    private Integer districtCode;

    private String districtName;

    private Long parentCustomerId;

    private String parentCustomerName;

    private Long sellerId;

    private String sellerName;

    private Long supplierId;

    private String supplierName;

    private String remark;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
