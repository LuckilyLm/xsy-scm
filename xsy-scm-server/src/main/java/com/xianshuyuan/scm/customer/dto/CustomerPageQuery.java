package com.xianshuyuan.scm.customer.dto;

import jakarta.validation.constraints.*;

public record CustomerPageQuery(@Min(1) long page, @Min(1) @Max(100) long pageSize, String keyword,
                                Long customerTypeId) {
}
