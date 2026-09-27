package com.xsy.scm.order.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class OrderOperationLogVO {
    private Long logId;
    private Long orderId;
    private String operationType;
    private String operator;
    private String reason;
    private Map<
            String,
            Object> beforeData;
    private Map<
            String,
            Object> afterData;
    private OffsetDateTime createdAt;
    private String createdBy;
    private String operatorName;
}
