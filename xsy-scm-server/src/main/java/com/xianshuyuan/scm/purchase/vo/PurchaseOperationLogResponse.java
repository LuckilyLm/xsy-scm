package com.xianshuyuan.scm.purchase.vo;
import com.fasterxml.jackson.databind.JsonNode; import java.time.OffsetDateTime;
public record PurchaseOperationLogResponse(Long id, String operationType, String operator, String reason, JsonNode beforeData, JsonNode afterData, OffsetDateTime createdAt) {}
