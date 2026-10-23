package com.xsy.scm.mall.address.domain.vo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class MallAddressVO {
    private Long addressId;
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
}
