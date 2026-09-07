package com.xianshuyuan.scm.customer.dto;

import com.xianshuyuan.scm.customer.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CustomerSaveRequest(Integer version, @NotBlank @Size(max = 64) String customerCode,
                                  @NotBlank @Size(max = 150) String name, @NotNull Long customerTypeId,
                                  @NotNull EnabledStatus status, @NotNull VisibilityPolicy visibilityPolicy,
                                  @NotNull List<@Valid CustomerSkuVisibilityRequest> visibilities) {
}
