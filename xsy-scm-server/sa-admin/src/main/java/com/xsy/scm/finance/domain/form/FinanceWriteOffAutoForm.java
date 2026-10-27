package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 按账单日期自动核销（FIFO）。
 *
 * <p>
 * 只选资金来源，不选目标：目标由服务端按「账单日期、单据 id」升序取未结单据，收款剩余额逐笔分配。 需要指定单据时仍走手工核销。
 */
@Data
public class FinanceWriteOffAutoForm {

    @NotBlank(message = "核销资金类型不能为空")
    private String sourceType;

    @NotNull(message = "核销资金单不能为空")
    @Positive(message = "核销资金单编号必须大于0")
    private Long sourceId;
}
