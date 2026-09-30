package com.xsy.scm.finance.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 对一张正常应付追加一张手工红字应付。 */
@Data
public class FinancePayableRedForm {

    @NotNull(message = "原应付单不能为空")
    @Positive(message = "原应付单编号必须大于0")
    private Long originalPayableId;

    @Size(max = 500, message = "红字原因长度不能超过500")
    private String reason;

    @NotEmpty(message = "至少填写一条红字明细")
    @Size(max = 100, message = "单次最多填写100条红字明细")
    @Valid
    private List<FinancePayableRedItemForm> items;
}
