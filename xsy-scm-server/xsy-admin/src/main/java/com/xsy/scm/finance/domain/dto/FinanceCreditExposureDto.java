package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一次查询读出结算主体的敞口数据。
 */
@Data
public class FinanceCreditExposureDto {
    private BigDecimal openReceivableAmount;
    private BigDecimal confirmedOrderAmount;
    private LocalDate earliestOverdueDate;
    private Boolean exposureVisible;
}
