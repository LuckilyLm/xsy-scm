package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 驳回秤读数。原因必填：驳回意味着「这台秤这次读的不算数」，
 * 没有原因就无法回答「为什么这行最后是人工录入的」。
 */
@Data
public class SortingScaleRejectForm {

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 200, message = "驳回原因不能超过200个字符")
    private String reason;
}
