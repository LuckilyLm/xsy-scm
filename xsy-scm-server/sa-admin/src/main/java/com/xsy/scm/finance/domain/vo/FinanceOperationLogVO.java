package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import com.xsy.scm.common.json.ScmOperatorSnapshotSerializer;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/** Finance-specific audit entry with before/after monetary snapshots. */
@Data
public class FinanceOperationLogVO {

    private Long id;

    private String businessType;

    private Long businessId;

    private String operationType;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String operator;

    private String reason;

    @JsonSerialize(using = ScmOperatorSnapshotSerializer.class)
    private Map<String, Object> beforeData;

    @JsonSerialize(using = ScmOperatorSnapshotSerializer.class)
    private Map<String, Object> afterData;

    private OffsetDateTime createdAt;
}
