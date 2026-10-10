package com.xsy.scm.mall.address.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName("mall_customer_address")
public class MallCustomerAddressEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String receiverName;
    private String receiverPhone;
    private Integer provinceCode;
    private String provinceName;
    private Integer cityCode;
    private String cityName;
    private Integer districtCode;
    private String districtName;
    private String detailAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String geomCrs;
    private Boolean isDefault;
    @Version
    private Integer version;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
