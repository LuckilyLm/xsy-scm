package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 应收应付列表行的公共列：金额口径、结算状态、事件日期与审计信息。
 *
 * <p>
 * 两种账只差对手方与单据标识，余额口径（金额、净额、已核销、未结、超核销）必须一致，因此放在同一基类里。
 */
@Data
public abstract class FinanceLedgerVO {

    private String sourceType;

    private Long sourceId;

    private String entryType;

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

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String updatedBy;
}
