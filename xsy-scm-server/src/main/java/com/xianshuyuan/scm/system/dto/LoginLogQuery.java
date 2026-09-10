package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginLogQuery extends AuditLogQuery {
    @Positive
    private Long userId;
    @Size(max = 64)
    private String username;
    @Pattern(regexp = "SUCCESS|FAILURE|LOCKED|LOGOUT")
    private String result;
}
