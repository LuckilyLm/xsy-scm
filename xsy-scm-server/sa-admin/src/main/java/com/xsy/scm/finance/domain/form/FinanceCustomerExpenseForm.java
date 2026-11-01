package com.xsy.scm.finance.domain.form;

import java.time.LocalDate;
import java.util.List;

import com.xsy.scm.common.util.ScmDecimalStrings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    /**
     * 批量登记同一月份的多客户费用：一行一个客户，说明共用。
     *
     * <p>
     * 月份与说明放在批次上，避免 200 行重复填写；客户与四项费用仍逐行校验，任意一行不合法整批回滚。
     */
    @Data
    public static class BatchAdd {
        @NotNull(message = "请选择费用月份")
        private LocalDate expenseMonth;
        @NotBlank(message = "请填写费用说明")
        @Size(max = 500, message = "费用说明不能超过500字")
        private String reason;
        @NotEmpty(message = "请至少填写一个客户")
        @Size(max = 200, message = "一次最多登记200个客户")
        private List<@Valid Row> rows;
    }

    @Data
    public static class Row {
        @NotNull(message = "请选择客户")
        @Positive(message = "客户编号无效")
        private Long customerId;
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
