package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionQuery {
    @Min(1) private long page = 1;
    @Min(1) @Max(100) private long pageSize = 20;
    @Size(max = 160) private String keyword;
    @Size(max = 64) private String module;
    @Pattern(regexp = "PAGE|ACTION|API") private String type;
    @Pattern(regexp = "ENABLED|DISABLED") private String status;
}
