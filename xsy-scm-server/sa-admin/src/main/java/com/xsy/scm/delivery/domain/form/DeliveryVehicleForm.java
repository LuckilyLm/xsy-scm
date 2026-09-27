package com.xsy.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

import java.util.List;
import java.math.BigDecimal;

@Data
public class DeliveryVehicleForm {
    @Positive(message = "记录编号必须为正数")
    private Long id;
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @NotBlank(message = "车牌号不能为空")
    @Size(max = 32, message = "车牌号长度不能超过32")
    private String vehicleNo;
    @Size(max = 64, message = "车辆类型长度不能超过64")
    private String vehicleType;
    @DecimalMin(value = "0", message = "载重不能小于0")
    @Digits(integer = 14, fraction = 4, message = "载重整数位不能超过14位且小数位不能超过4位")
    private BigDecimal loadWeight;
    @DecimalMin(value = "0", message = "容积不能小于0")
    @Digits(integer = 14, fraction = 4, message = "容积整数位不能超过14位且小数位不能超过4位")
    private BigDecimal loadVolume;
    @NotNull(message = "状态不能为空")
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "车辆状态无效")
    private String status = ScmEnableStatusEnum.ENABLED.name();
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
