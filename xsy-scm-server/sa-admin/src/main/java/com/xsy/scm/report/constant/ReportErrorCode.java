package com.xsy.scm.report.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.common.error.ScmExportErrorCode;

/**
 * 报表查询、导出与每日清单可用性错误码。
 *
 * <p>
 * 已发布的报表错误码为 41110–41112；每日清单使用 41150，避开配送、分拣与财务已占用区间。
 */
@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ScmErrorCode {

    /**
     * 起止日期缺失或倒序。报表不接受无边界扫描：没有日期就没有可复现的口径， 「导出 = 列表」也无从保证。
     */
    REPORT_DATE_RANGE_REQUIRED(41110, "请选择完整且顺序正确的查询日期范围"),

    /** 查询跨度超过 {@code ScmReportTimeRangeResolver.MAX_SPAN_DAYS}。 */
    REPORT_DATE_RANGE_TOO_LARGE(41111, "查询日期跨度超过上限，请缩小日期范围后重试"),

    REPORT_STATEMENT_TOO_LARGE(41113, "客户历史财务事实超过单次对账上限，请联系管理员分期处理"),
    REPORT_STATEMENT_UNAVAILABLE(41114, "对账单不存在或当前无权查看完整版本"),
    REPORT_STATEMENT_EMPTY(41115, "当前授权范围内没有可生成对账单的财务事实"),
    REPORT_DAILY_UNAVAILABLE(41150, "该日期的采购清单尚未生成或不在授权范围内");

    /** Shared by finance and report exports; the wire code remains 41112. */
    public static final ScmErrorCode REPORT_EXPORT_ROW_LIMIT_EXCEEDED = ScmExportErrorCode.EXPORT_ROW_LIMIT_EXCEEDED;

    private final int code;

    private final String msg;
}
