package com.xsy.scm.order.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 手动锁单/解锁入参。锁单原因可选：自动锁单是「发车满 7 天」，手动锁单的原因往往就是业务判断，写下来便于追溯。
 */
@Data
public class OrderLockForm {
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @Size(max = 200, message = "锁单原因不能超过200个字符")
    private String reason;
}
