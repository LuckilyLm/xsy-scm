package com.xsy.scm.report.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScmFinanceAgingBucketEnum {
    UNKNOWN("到期日未设置"),
    NOT_DUE("未逾期"),
    DAYS_1_30("逾期1至30天"),
    DAYS_31_60("逾期31至60天"),
    DAYS_61_90("逾期61至90天"),
    DAYS_91_180("逾期91至180天"),
    OVER_180("逾期超过180天");

    private final String description;
}
