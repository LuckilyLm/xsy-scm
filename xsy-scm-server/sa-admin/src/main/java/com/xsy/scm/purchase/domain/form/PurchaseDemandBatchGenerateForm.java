package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PurchaseDemandBatchGenerateForm {
    @NotNull(message = "净需求计算批次不能为空")
    @Positive(message = "净需求计算批次编号必须大于零")
    private Long batchId;
}
