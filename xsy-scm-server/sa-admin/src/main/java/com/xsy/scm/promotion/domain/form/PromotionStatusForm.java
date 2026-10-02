package com.xsy.scm.promotion.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 活动启停。
 */
@Data
public class PromotionStatusForm {

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    @NotBlank(message = "目标状态不能为空")
    @Size(max = 16, message = "状态不能超过16个字符")
    private String status;
}
