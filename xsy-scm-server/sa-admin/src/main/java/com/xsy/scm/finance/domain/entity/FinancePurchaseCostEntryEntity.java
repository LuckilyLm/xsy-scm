package com.xsy.scm.finance.domain.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("xsy_finance_purchase_cost_entry")
public class FinancePurchaseCostEntryEntity {
    private Long id;
    private Long adjustmentId;
    private Long receiptItemId;
    private Long originalPayableId;
    private BigDecimal amount;
    private String createdBy;
}
