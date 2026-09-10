package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {
    @NotBlank
    @Size(max = 64)
    private String username;
    @NotBlank
    @Size(max = 100)
    private String displayName;
    @NotBlank
    @Size(min = 12, max = 72)
    private String password;
    @Positive
    private Long departmentId;
    @Email
    @Size(max = 254)
    private String email;
    @Size(max = 32)
    private String phone;
}
