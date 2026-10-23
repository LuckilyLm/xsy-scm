package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 每次人工分拣的一笔新增量。只追加待确认分次，不自动把任务明细标为完成。
 */
@Data
public class SortingPartForm {
    @NotNull
    @Positive
    private Long taskItemId;

    @NotNull
    @Min(0)
    private Integer version;

    @NotNull
    @DecimalMin(value = "0.0000", inclusive = false)
    @Digits(integer = 14, fraction = 4)
    private BigDecimal quantity;

    @Size(max = 500)
    private String note;
}
