package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务级动作（完成 / 取消 / 重开 / 正式打印）的统一入参： {@code version} 必带，{@code reason} 是否必填由动作决定（服务侧校验，与配送线路同口径）。
 */
@Data
public class SortingActionForm {
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    @Size(max = 500, message = "操作原因不能超过500个字符")
    private String reason;
}
