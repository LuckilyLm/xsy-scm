package com.xsy.scm.finance.domain.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 财务入账时读取的结算主体账期，仅供历史资料，不回写客户资料。 */
@Data
public class FinanceCreditRuleDto {
    private Long customerId;
    private String type;
    private Integer value;
    private String unit;
    private Integer settleDay;
    private BigDecimal amountThreshold;
}
