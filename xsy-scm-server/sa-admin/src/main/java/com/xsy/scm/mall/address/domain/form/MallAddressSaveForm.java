package com.xsy.scm.mall.address.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class MallAddressSaveForm {
    @NotBlank(message = "收货人不能为空")
    @Size(max = 64, message = "收货人不能超过64个字符")
    private String receiverName;
    @NotBlank(message = "联系电话不能为空")
    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String receiverPhone;
    private Integer provinceCode;
    @Size(max = 32, message = "省份名称不能超过32个字符")
    private String provinceName;
    private Integer cityCode;
    @Size(max = 64, message = "城市名称不能超过64个字符")
    private String cityName;
    private Integer districtCode;
    @Size(max = 64, message = "区县名称不能超过64个字符")
    private String districtName;
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址不能超过255个字符")
    private String detailAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Boolean isDefault;
}
