package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 删除客户。
 *
 * <p>
 * 删除命令通过 POST body 携带 {@code customerId} 与 {@code version}，以乐观锁拒绝过期请求。
 */
@Data
public class CustomerDeleteForm {

    @NotNull(message = "客户 ID 不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
