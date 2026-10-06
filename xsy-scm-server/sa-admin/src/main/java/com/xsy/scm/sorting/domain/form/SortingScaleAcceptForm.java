package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 接受秤读数：把<b>该读数</b>写进分拣结果。
 *
 * <p>
 * 刻意不提供「改成别的数量再接受」：那等于用一次「接受」掩盖一次人工改数。 读数不对就先驳回，再走人工录入（那条路径会要求填写差异原因）。
 */
@Data
public class SortingScaleAcceptForm {

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
