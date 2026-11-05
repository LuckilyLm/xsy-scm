package com.xsy.scm.report.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
public class ScmFinanceAgingRowVO {
    private String accountType;
    private Long documentId;
    private String documentNo;
    private Long sourceId;
    private String sourceNo;
    private Long counterpartyId;
    private String counterpartyName;
    private Long settlementCustomerId;
    private String settlementCustomerName;
    private LocalDate dueDate;
    private Integer overdueDays;
    private String agingBucket;
    private OffsetDateTime eventAt;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal redAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal netAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal writtenOffAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal openAmount;
}
