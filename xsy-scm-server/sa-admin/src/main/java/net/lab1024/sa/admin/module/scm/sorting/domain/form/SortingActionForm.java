package net.lab1024.sa.admin.module.scm.sorting.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务级动作（完成 / 取消 / 重开 / 正式打印）的统一入参：
 * {@code version} 必带，{@code reason} 是否必填由动作决定（服务侧校验，与配送线路同口径）。
 */
@Data
public class SortingActionForm {
    @NotNull
    @Min(0)
    private Integer version;

    @Size(max = 500)
    private String reason;
}
