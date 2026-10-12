package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/** 应付事实及其只读派生余额。 */
@Data
public class FinancePayableVO {

    private Long payableId;

    private String payableNo;

    private Long purchaseOrderId;

    private String purchaseOrderNo;

    private Long supplierId;

    private String supplierName;

    private String sourceType;

    private Long sourceId;

    private String entryType;

    private Long originalPayableId;

    private String originalPayableNo;

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
