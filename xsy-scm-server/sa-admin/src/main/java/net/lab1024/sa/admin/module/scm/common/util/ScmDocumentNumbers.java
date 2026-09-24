package net.lab1024.sa.admin.module.scm.common.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * SCM 单据编号的唯一拼接规则：前缀 + yyyyMMdd（Asia/Shanghai）+ 至少 6 位序号。
 *
 * <p>此前这段逻辑在 7 个 {@code *NumberGenerator} 里各有一份实现（其中 6 份逐字节相同、
 * 订单那份只是把常量大写内联），改一次补零口径要同步 7 处。各生成器现在只保留自己的
 * 前缀常量与序列取号，拼接一律走这里。
 *
 * <p>时间基准刻意取 Asia/Shanghai 而不是系统默认时区：单号里的日期段是业务日期，
 * 不能随部署机器的时区漂移。序号超过 999999 时自然扩位，既不截断也不报错。
 */
public final class ScmDocumentNumbers {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private ScmDocumentNumbers() {
    }

    public static String format(String prefix, long number) {
        return prefix
                + LocalDate.now(BUSINESS_ZONE).format(DATE)
                + String.format(Locale.ROOT, "%06d", number);
    }
}
