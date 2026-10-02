package com.xsy.scm.finance.domain.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Account exposure read in one PostgreSQL snapshot. */
@Data
public class FinanceCreditExposureDto {
    private BigDecimal openReceivableAmount;
    private BigDecimal confirmedOrderAmount;
    private LocalDate earliestOverdueDate;
    private Boolean exposureVisible;
}
