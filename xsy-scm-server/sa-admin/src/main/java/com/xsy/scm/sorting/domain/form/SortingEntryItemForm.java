package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.sorting.constant.ScmSortingResultEnum;

import java.math.BigDecimal;

/**
 * 一行明细的分拣结果。非标品这里录的是实重，单位即明细行上的销售单位快照
 * （P1 不做单位换算，也不记毛重 / 皮重 / 净重）。
 */
@Data
public class SortingEntryItemForm {
    @NotNull(message = "分拣明细 ID 不能为空")
    @Positive(message = "分拣明细 ID 必须大于0")
    private Long id;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    /**
     * 允许 0：整行缺货是合法结果，不能靠「不提交这行」表达，否则任务永远无法完成。
     */
    @NotNull(message = "分拣数量不能为空")
    @DecimalMin(value = "0", message = "分拣数量不能小于0")
    @Digits(integer = 14, fraction = 4, message = "分拣数量最多14位整数和4位小数")
    private BigDecimal sortedQuantity;

    @NotBlank(message = "分拣结果不能为空")
    @ScmEnumValue(enumClass = ScmSortingResultEnum.class, message = "分拣结果无效")
    private String result;

    @Size(max = 500, message = "差异原因不能超过500个字符")
    private String reason;
}
