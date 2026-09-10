package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OperationLogQuery extends AuditLogQuery {
    @Positive private Long actorUserId;
    @Size(max = 64) private String module;
    @Size(max = 100) private String operationCode;
    @Size(max = 100) private String targetType;
    @Size(max = 100) private String targetId;
    private Boolean success;
}
