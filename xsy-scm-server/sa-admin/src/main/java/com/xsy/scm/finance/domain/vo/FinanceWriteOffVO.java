package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 核销流水分页行或核销命令结果。 */
@Data
public class FinanceWriteOffVO {

    private Long writeOffId;

    private String writeOffNo;

    private String sourceType;

    private Long sourceId;

    private String sourceNo;

    private String sourceName;

    private String targetType;

    private Long targetId;

    private String targetNo;

    private String targetName;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    private String entryType;

    private Long reverseOfId;

    private String reason;

    private OffsetDateTime writtenOffAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String operator;
}
