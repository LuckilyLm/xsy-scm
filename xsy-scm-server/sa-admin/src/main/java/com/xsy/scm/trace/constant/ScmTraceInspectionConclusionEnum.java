package com.xsy.scm.trace.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScmTraceInspectionConclusionEnum {
    QUALIFIED("合格"),
    UNQUALIFIED("不合格"),
    UNDETERMINED("未判定");

    private final String desc;
}
