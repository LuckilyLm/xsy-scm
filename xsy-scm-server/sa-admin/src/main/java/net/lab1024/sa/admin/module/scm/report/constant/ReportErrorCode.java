package net.lab1024.sa.admin.module.scm.report.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * Finance R0 报表域错误码。只读域，因此只有「查询边界不合法」与「导出规模超限」两类，没有写入冲突码。
 *
 * <p>码段 41110–41112，紧邻 delivery 的 41100–41109。<b>411xx 的实际占用必须现查现用</b>
 * （与 {@code InventoryErrorCode} 同一纪律）：那是写下时的状态，会过期。
 */
@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ScmErrorCode {

    /**
     * 起止日期缺失或倒序。报表不接受无边界扫描：没有日期就没有可复现的口径，
     * 「导出 = 列表」也无从保证。
     */
    REPORT_DATE_RANGE_REQUIRED(41110, "请选择完整且顺序正确的查询日期范围"),

    /** 查询跨度超过 {@code ScmReportTimeRangeResolver.MAX_SPAN_DAYS}。 */
    REPORT_DATE_RANGE_TOO_LARGE(41111, "查询日期跨度超过上限，请缩小日期范围后重试"),

    /**
     * 导出命中行数超过上限。明确拒绝而不是静默截断：
     * 既有商品 / 采购导出是「clamp pageSize 后少导一部分」，那会让使用者把部分结果当全部，
     * 报表要求列表口径与导出口径一致，因此超出必须报错。
     */
    REPORT_EXPORT_ROW_LIMIT_EXCEEDED(41112, "当前筛选结果超过导出行数上限，请缩小日期范围或筛选条件后重试");

    private final int code;

    private final String msg;
}
