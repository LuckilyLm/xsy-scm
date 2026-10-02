package com.xsy.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;

@Data
public class ScmSupplierStatementItemVO {
    private Long statementId;
    private Integer lineNo;
    private String factType;
    private Long factId;
    private OffsetDateTime eventAt;
    private String documentNo;
    private String relatedNo;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal payableDelta;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal paymentDelta;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal writeOffDelta;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal payableBalance;
}
