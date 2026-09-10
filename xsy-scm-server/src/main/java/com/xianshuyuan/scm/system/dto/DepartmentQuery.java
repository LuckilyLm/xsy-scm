package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentQuery {
    @Min(1)
    private long page = 1;
    @Min(1)
    @Max(100)
    private long pageSize = 20;
    @Size(max = 100)
    private String keyword;
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;
    @Positive
    private Long parentId;
}
