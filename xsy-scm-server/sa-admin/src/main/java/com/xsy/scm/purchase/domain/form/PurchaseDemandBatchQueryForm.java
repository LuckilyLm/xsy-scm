package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 冻结批次回看入参（只读）。
 *
 * <p>
 * 只有一个批次号：回看不接受任何筛选或分页条件 —— 批次本身就是一次冻结的完整输入，让调用方再加筛选会让「回看」和「重新计算」的边界变模糊。
 */
@Data
public class PurchaseDemandBatchQueryForm {

    @NotNull(message = "净需求计算批次不能为空")
    @Positive(message = "净需求计算批次编号必须大于零")
    private Long batchId;
}
