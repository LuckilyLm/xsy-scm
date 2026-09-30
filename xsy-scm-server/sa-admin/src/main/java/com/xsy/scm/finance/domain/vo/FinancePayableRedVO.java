package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 手工红字应付命令结果。 */
@Data
public class FinancePayableRedVO {

    private Long payableId;

    private String payableNo;

    private Long originalPayableId;

    private Long supplierId;

    private String supplierName;

    private String entryType;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    private OffsetDateTime eventAt;

    private String reason;

    private int itemCount;
}
