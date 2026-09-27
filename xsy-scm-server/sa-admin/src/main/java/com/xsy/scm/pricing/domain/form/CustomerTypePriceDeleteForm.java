package com.xsy.scm.pricing.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerTypePriceDeleteForm {
    @NotNull(message = "客户类型价 ID 不能为空")
    private Long customerTypePriceId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
