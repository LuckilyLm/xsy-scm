package com.xianshuyuan.scm.system.dto;

import java.time.OffsetDateTime;

public record LoginLogResponse(long id, Long userId, String usernameSnapshot, String result,
                               String failureReasonCode, String ip, String userAgent, OffsetDateTime occurredAt) {
}
