package com.xsy.scm.promotion.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 发券：给某客户发 N 张某模板的券。
 */
@Data
public class PromotionCouponIssueForm {

    @NotNull(message = "券模板不能为空")
    @Positive(message = "券模板 ID 必须大于0")
    private Long couponId;

    @NotNull(message = "客户不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;

    @Min(value = 1, message = "发券张数必须至少为1")
    @Max(value = 200, message = "单次最多发200张")
    private Integer quantity = 1;
}
