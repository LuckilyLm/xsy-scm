package com.xianshuyuan.scm.customer.dto;
import com.xianshuyuan.scm.customer.entity.EnabledStatus; import jakarta.validation.constraints.*;
public record CustomerTypeSaveRequest(Integer version,@NotBlank @Size(max=64) String typeCode,@NotBlank @Size(max=100) String name,@NotNull EnabledStatus status) {}
