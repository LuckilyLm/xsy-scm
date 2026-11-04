package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PurchaseDemandBatchGenerateForm {
    @NotNull(message = "采购需求汇总不能为空")
    @Positive(message = "采购需求汇总编号必须大于零")
    private Long batchId;
}
