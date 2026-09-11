package com.xianshuyuan.scm.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CustomerTypePriceBatchRequest(
        @NotBlank @Size(max = 100) String batchKey,
        @NotEmpty @Size(max = 500) List<@Valid CustomerTypePriceBatchRowRequest> rows) {
}
