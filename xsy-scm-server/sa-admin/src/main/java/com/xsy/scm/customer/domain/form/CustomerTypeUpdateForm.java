package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑客户类型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerTypeUpdateForm extends CustomerTypeAddForm {

    @NotNull(message = "客户类型 ID 不能为空")
    @Positive(message = "客户类型 ID 必须大于0")
    private Long typeId;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
