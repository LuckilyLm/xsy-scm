package com.xsy.scm.report.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScmFinanceAgingSummaryVO {
    private String agingBucket;
    private Long documentCount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal openAmount;
}
