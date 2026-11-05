package com.xsy.scm.mall.cart.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MallCartQuantityForm {
    @NotBlank(message = "数量不能为空")
    @Pattern(regexp = "^\\d{1,10}(\\.\\d{1,4})?$", message = "数量必须为大于0、最多4位小数的数字")
    private String quantity;
}
