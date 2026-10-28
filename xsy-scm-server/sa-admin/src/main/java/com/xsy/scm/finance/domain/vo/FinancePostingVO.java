package com.xsy.scm.finance.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import lombok.Data;

/**
 * 过账记录：登记内容 + 由作废事实推导出的有效状态。
 *
 * <p>
 * 状态不落过账表：{@code VOIDED} 表示 {@code xsy_finance_posting_void} 有对应行， 作废人、时间与原因一并从这里带出。
 */
@Data
public class FinancePostingVO {

    private Long postingId;

    private String postingNo;

    private Long customerId;

    private String customerName;

    private Long settlementCustomerId;

    private String settlementCustomerName;

    private LocalDate documentDate;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    /**
     * 税点百分比；单据上没写时为 {@code null}。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal taxRate;

    private String refundStatus;

    private String remark;

    /**
     * {@code ScmFinancePostingStatusEnum}：{@code NORMAL / VOIDED}。
     */
    private String status;

    private OffsetDateTime voidedAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String voidedBy;

    private String voidReason;

    private Integer version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String updatedBy;
}
