package com.xsy.scm.finance.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 一笔收款或供应商付款向多个应收 / 应付分配。 */
@Data
public class FinanceWriteOffAddForm {

    @NotNull(message = "核销资金类型不能为空")
    private String sourceType;

    @NotNull(message = "核销资金单不能为空")
    @Positive(message = "核销资金单编号必须大于0")
    private Long sourceId;

    @NotEmpty(message = "至少填写一条核销目标")
    @Size(max = 100, message = "单次最多核销100条目标")
    @Valid
    private List<FinanceWriteOffAddItemForm> items;
}
