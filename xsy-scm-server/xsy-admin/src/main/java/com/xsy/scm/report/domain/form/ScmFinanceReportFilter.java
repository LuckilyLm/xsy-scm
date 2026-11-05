package com.xsy.scm.report.domain.form;

import com.xsy.scm.report.support.ScmReportDateFilter;

/**
 * 分页明细与导出共用的日期和关键字筛选。
 */
public interface ScmFinanceReportFilter extends ScmReportDateFilter {

    String getKeyword();
}
