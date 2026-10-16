package com.xsy.scm.mall.address.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
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
    @NotNull(message = "请选择省份")
    private Integer provinceCode;
    @NotBlank(message = "省份名称不能为空")
    @Size(max = 32, message = "省份名称不能超过32个字符")
    private String provinceName;
    @NotNull(message = "请选择城市")
    private Integer cityCode;
    @NotBlank(message = "城市名称不能为空")
    @Size(max = 64, message = "城市名称不能超过64个字符")
    private String cityName;
    @NotNull(message = "请选择区县")
    private Integer districtCode;
    @NotBlank(message = "区县名称不能为空")
    @Size(max = 64, message = "区县名称不能超过64个字符")
    private String districtName;
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址不能超过255个字符")
    private String detailAddress;
    @NotNull(message = "请在地图上选择收货位置")
    @DecimalMin(value = "-180", message = "经度不能小于-180")
    @DecimalMax(value = "180", message = "经度不能大于180")
    private BigDecimal longitude;
    @NotNull(message = "请在地图上选择收货位置")
    @DecimalMin(value = "-90", message = "纬度不能小于-90")
    @DecimalMax(value = "90", message = "纬度不能大于90")
    private BigDecimal latitude;
    private Boolean isDefault;
}
