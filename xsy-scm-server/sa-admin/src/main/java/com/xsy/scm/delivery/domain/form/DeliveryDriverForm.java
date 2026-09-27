package com.xsy.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

import java.util.List;
import java.math.BigDecimal;

@Data
public class DeliveryDriverForm {
    @Positive(message = "记录编号必须为正数")
    private Long id;
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @NotBlank(message = "司机编码不能为空")
    @Size(max = 64, message = "司机编码长度不能超过64")
    private String driverCode;
    @NotBlank(message = "司机姓名不能为空")
    @Size(max = 100, message = "司机姓名长度不能超过100")
    private String driverName;
    @NotBlank(message = "联系电话不能为空")
    @Size(max = 32, message = "联系电话长度不能超过32")
    @Pattern(regexp = "[0-9+() -]{5,32}")
    private String phone;
    /**
     * 绑定的系统员工 id；启用状态必填（历史行可为空，但一旦保存为 ENABLED 就必须有归属）。
     */
    @Positive(message = "员工编号必须为正数")
    private Long employeeId;
    @NotNull(message = "状态不能为空")
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "司机状态无效")
    private String status = ScmEnableStatusEnum.ENABLED.name();
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
