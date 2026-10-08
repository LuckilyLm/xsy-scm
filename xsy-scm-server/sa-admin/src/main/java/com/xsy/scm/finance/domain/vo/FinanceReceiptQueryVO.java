package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** 收款事实与有效额、已用额、待核销额派生值。 */
@Data
public class FinanceReceiptQueryVO {
    private Boolean walletFunding;

    private Long receiptId;

    private String receiptNo;

    private Long customerId;

    private String customerName;
    private Long settlementCustomerId;
    private String settlementCustomerName;

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

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
