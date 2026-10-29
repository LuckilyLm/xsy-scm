package com.xsy.scm.finance.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("xsy_finance_customer_expense")
public class FinanceCustomerExpenseEntity {
    private Long id;
    private String expenseNo;
    private Long customerId;
    private String customerNameSnapshot;
    private LocalDate expenseMonth;
    private BigDecimal taxAmount;
    private BigDecimal serviceAmount;
    private BigDecimal freightAmount;
    private BigDecimal commissionAmount;
    private String reason;
    private OffsetDateTime createdAt;
    private String createdBy;
}
