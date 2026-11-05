package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 批次回看入参（只读）。
 *
 * <p>
 * 只有一个批次号：批次本身就是一次计算固定的完整输入，不再接受筛选或分页，避免「回看」变成「重新计算」。
 */
@Data
public class PurchaseDemandBatchQueryForm {

    @NotNull(message = "采购需求汇总不能为空")
    @Positive(message = "采购需求汇总编号必须大于零")
    private Long batchId;
}
