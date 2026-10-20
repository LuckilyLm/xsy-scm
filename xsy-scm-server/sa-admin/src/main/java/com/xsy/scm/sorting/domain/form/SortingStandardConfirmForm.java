package com.xsy.scm.sorting.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class SortingStandardConfirmForm {
    @NotNull(message = "任务版本不能为空")
    @Min(value = 0, message = "任务版本不能小于0")
    private Integer version;

    @NotNull(message = "请选择待确认明细")
    @Size(min = 1, max = 500, message = "一次请选择1至500条明细")
    private List<@NotNull(message = "明细不能为空") @Valid Item> items;

    public record Item(
            @NotNull(message = "明细编号不能为空") @Positive(message = "明细编号必须大于0") Long id,
            @NotNull(message = "明细版本不能为空") @Min(value = 0, message = "明细版本不能小于0") Integer version) {
    }
}
