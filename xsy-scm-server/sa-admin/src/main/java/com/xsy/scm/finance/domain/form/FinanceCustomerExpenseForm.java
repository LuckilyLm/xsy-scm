package com.xsy.scm.finance.domain.form;

import java.time.LocalDate;

import com.xsy.scm.common.util.ScmDecimalStrings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

public final class FinanceCustomerExpenseForm {
    private FinanceCustomerExpenseForm() {
    }

    @Data
    public static class Add {
        @NotNull(message = "请选择客户")
        @Positive(message = "客户编号无效")
        private Long customerId;
        @NotNull(message = "请选择费用月份")
        private LocalDate expenseMonth;
        @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "实际商品成本格式无效")
        private String goodsCostAmount;
        @NotBlank(message = "请填写税费，无费用时填0")
        @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "税费金额格式无效")
        private String taxAmount;
        @NotBlank(message = "请填写服务费，无费用时填0")
        @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "服务费金额格式无效")
        private String serviceAmount;
        @NotBlank(message = "请填写运费，无费用时填0")
        @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "运费金额格式无效")
        private String freightAmount;
        @NotBlank(message = "请填写扣点金额，无费用时填0")
        @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "扣点金额格式无效")
        private String commissionAmount;
        @NotBlank(message = "请填写费用说明")
        @Size(max = 500, message = "费用说明不能超过500字")
        private String reason;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @NotNull(message = "请选择费用月份")
        private LocalDate expenseMonth;
        @Positive(message = "客户编号无效")
        private Long customerId;
    }

    @Data
    public static class Void {
        @NotNull(message = "请选择费用记录")
        @Positive(message = "费用记录编号无效")
        private Long expenseId;
        @NotBlank(message = "请填写作废原因")
        @Size(max = 500, message = "作废原因不能超过500字")
        private String reason;
    }
}
