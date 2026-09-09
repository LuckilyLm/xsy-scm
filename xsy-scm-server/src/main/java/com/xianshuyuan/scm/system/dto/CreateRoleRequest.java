package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;

public record CreateRoleRequest(
        @NotBlank @Size(max = 102) @Pattern(regexp = "\s*[A-Za-z0-9][A-Za-z0-9._-]{0,99}\s*") String roleCode,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description) {}
