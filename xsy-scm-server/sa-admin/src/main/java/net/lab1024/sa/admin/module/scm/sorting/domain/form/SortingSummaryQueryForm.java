package net.lab1024.sa.admin.module.scm.sorting.domain.form;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 按商品汇总的只读视角：跨订单按 SKU + 单位分组，**不跨单位求和**。
 * 本视角不承载任何录入动作（裁决补充第 19 条）。
 */
@Data
public class SortingSummaryQueryForm extends PageParam {
    @Size(max = 100)
    private String keyword;

    @Positive
    private Long warehouseId;

    @Pattern(regexp = "PENDING|SORTING|COMPLETED|CANCELLED")
    private String taskStatus;
}
