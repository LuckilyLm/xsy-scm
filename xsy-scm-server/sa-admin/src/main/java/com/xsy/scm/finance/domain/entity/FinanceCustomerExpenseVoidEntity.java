package com.xsy.scm.finance.domain.entity;

import java.time.OffsetDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("xsy_finance_customer_expense_void")
public class FinanceCustomerExpenseVoidEntity {
    private Long id;
    private Long expenseId;
    private String reason;
    private OffsetDateTime createdAt;
    private String createdBy;
}
