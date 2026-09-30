package com.xsy.scm.report.domain.form;

import com.xsy.scm.report.support.ScmReportDateFilter;

/** Shared date and keyword filters for paginated Finance report details and exports. */
public interface ScmFinanceReportFilter extends ScmReportDateFilter {

    String getKeyword();
}
