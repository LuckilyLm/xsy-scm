package com.xianshuyuan.scm.order.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record OrderOperationLogResponse(Long id, String operationType, String operator, String reason,
                                        JsonNode beforeData, JsonNode afterData, OffsetDateTime createdAt) {
}
