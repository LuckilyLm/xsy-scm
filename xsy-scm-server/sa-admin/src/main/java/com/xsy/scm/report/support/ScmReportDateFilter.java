package com.xsy.scm.report.support;

import java.time.LocalDate;

/** 报表输入的是北京时间的起止日期闭区间。转换为半开时间区间只能在统一日期转换器中处理，不能由各报表自行换算。 */
public interface ScmReportDateFilter {

    LocalDate getStartDate();

    LocalDate getEndDate();
}
