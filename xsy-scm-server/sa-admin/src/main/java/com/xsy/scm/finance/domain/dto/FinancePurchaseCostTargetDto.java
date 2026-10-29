package com.xsy.scm.finance.domain.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class FinancePurchaseCostTargetDto {
    private Long payableId;
    private BigDecimal lineAmount;
    private BigDecimal lineRedAmount;
    private BigDecimal amount;
    private BigDecimal redAmount;
}
