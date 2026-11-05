package com.xsy.scm.trace.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScmTraceInspectionStatusEnum {

    DRAFT("草稿"),

    PUBLISHED("已发布"),

    WITHDRAWN("已撤回");

    private final String desc;
}
