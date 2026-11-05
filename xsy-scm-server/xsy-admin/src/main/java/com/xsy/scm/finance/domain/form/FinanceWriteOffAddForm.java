package com.xsy.scm.finance.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 一笔收款或供应商付款向多个应收 / 应付分配。
 */
@Data
public class FinanceWriteOffAddForm {

    @NotNull(message = "用于抵扣的收付款类型不能为空")
    private String sourceType;

    @NotNull(message = "用于抵扣的收付款单不能为空")
    @Positive(message = "用于抵扣的收付款单编号必须大于0")
    private Long sourceId;

    @NotEmpty(message = "至少选择一张待结算账单")
    @Size(max = 100, message = "单次最多抵扣100张账单")
    @Valid
    private List<FinanceWriteOffAddItemForm> items;
}
