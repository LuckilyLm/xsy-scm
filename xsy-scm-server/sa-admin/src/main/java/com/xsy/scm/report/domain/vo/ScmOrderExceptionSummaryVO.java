package com.xsy.scm.report.domain.vo;

import lombok.Data;

@Data
public class ScmOrderExceptionSummaryVO {
    private String exceptionType;
    private Long exceptionCount;
    private Long orderCount;
}
