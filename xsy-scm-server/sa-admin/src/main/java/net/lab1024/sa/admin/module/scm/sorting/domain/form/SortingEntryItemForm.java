package net.lab1024.sa.admin.module.scm.sorting.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 一行明细的分拣结果。非标品这里录的是实重，单位即明细行上的销售单位快照
 * （P1 不做单位换算，也不记毛重 / 皮重 / 净重）。
 */
@Data
public class SortingEntryItemForm {
    @NotNull
    @Positive
    private Long id;

    @NotNull
    @Min(0)
    private Integer version;

    /**
     * 允许 0：整行缺货是合法结果，不能靠「不提交这行」表达，否则任务永远无法完成。
     */
    @NotNull
    @DecimalMin("0")
    @Digits(integer = 14, fraction = 4)
    private BigDecimal sortedQuantity;

    @NotBlank
    @Pattern(regexp = "NORMAL|SHORT|OUT_OF_STOCK|OVER")
    private String result;

    @Size(max = 500)
    private String reason;
}
