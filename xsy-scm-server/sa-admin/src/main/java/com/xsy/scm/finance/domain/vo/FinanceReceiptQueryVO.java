package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 收款事实与有效额、已用额、待核销额派生值。 */
@Data
public class FinanceReceiptQueryVO {

    private Long receiptId;

    private String receiptNo;

    private Long customerId;

    private String customerName;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal effectiveAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal usedAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal pendingWriteOffAmount;

    private String method;

    private String entryType;

    private Long reverseOfId;

    private String reverseOfNo;

    private String reason;

    private OffsetDateTime receivedAt;

    private String externalReference;

    private String remark;
}
