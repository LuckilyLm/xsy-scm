package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/** 应收事实及其只读派生余额。 */
@Data
public class FinanceReceivableVO {

    private Long receivableId;

    private String receivableNo;

    private Long orderId;

    private String orderNo;

    private Long customerId;

    private String customerName;
    private Long settlementCustomerId;
    private String settlementCustomerName;

    private String sourceType;

    private Long sourceId;

    private String entryType;

    private Long originalReceivableId;

    private String originalReceivableNo;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal netAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal writtenOffAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal openAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal overAppliedAmount;

    private String settleState;

    private OffsetDateTime eventAt;

    private LocalDate dueDate;
    private String reason;
}
