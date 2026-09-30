package com.xsy.scm.finance.domain.vo;

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

    private String operator;

    private String reason;

    private Map<String, Object> beforeData;

    private Map<String, Object> afterData;

    private OffsetDateTime createdAt;
}
