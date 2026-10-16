package com.xsy.scm.order.domain.form;

import java.math.BigDecimal;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class OrderAddressForm {
    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 100, message = "收货人姓名不能超过100个字符")
    private String receiverName;
    @NotBlank(message = "收货人手机号不能为空")
    @Size(max = 32, message = "收货人手机号不能超过32个字符")
    private String receiverPhone;
    @NotBlank(message = "收货地址不能为空")
    @Size(max = 500, message = "收货地址不能超过500个字符")
    private String address;
    private Integer provinceCode;
    private String provinceName;
    private Integer cityCode;
    private String cityName;
    private Integer districtCode;
    private String districtName;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String geomCrs;
}
