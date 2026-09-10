package com.xianshuyuan.scm.system.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MenuTreeQuery {
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;
    private Boolean visible;
}
