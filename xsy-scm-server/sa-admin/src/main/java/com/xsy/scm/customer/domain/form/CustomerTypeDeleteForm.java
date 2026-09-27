package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 删除客户类型。
 *
 * <p>删除前检查客户类型是否仍被活动客户引用。
 */
@Data
public class CustomerTypeDeleteForm {

    @NotNull(message = "客户类型 ID 不能为空")
    @Positive(message = "客户类型 ID 必须大于0")
    private Long typeId;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
