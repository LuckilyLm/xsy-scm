package com.xsy.scm.common.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * SCM 编号拼接规则：单据号 = 前缀 + yyyyMMdd（Asia/Shanghai）+ 至少 6 位序号；主数据编码 = 前缀 + 至少 6 位序号，不带日期。
 *
 * <p>
 * 时间基准刻意取 Asia/Shanghai 而不是系统默认时区：单号里的日期段是业务日期，不能随部署机器的时区漂移。序号超过 999999 时自然扩位，既不截断也不报错。
 */
public final class ScmDocumentNumbers {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private ScmDocumentNumbers() {
    }

    public static String format(String prefix, long number) {
        return prefix + LocalDate.now(BUSINESS_ZONE).format(DATE) + String.format(Locale.ROOT, "%06d", number);
    }

    /**
     * 主数据编码（客户 / 供应商 / 仓库等）：主数据没有业务日期语义，只拼前缀与序号。
     */
    public static String masterCode(String prefix, long number) {
        return prefix + String.format(Locale.ROOT, "%06d", number);
    }
}
