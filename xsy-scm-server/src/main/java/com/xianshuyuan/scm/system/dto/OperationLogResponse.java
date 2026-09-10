package com.xianshuyuan.scm.system.dto;

import java.time.OffsetDateTime;

/** Summary projection deliberately excludes raw request and before/after JSON payloads. */
public record OperationLogResponse(long id, Long actorUserId, String actorNameSnapshot, String module,
        String operationCode, String targetType, String targetId, boolean success, Integer errorCode,
        OffsetDateTime occurredAt) {
}
