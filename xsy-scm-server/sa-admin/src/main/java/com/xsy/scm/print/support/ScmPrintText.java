package com.xsy.scm.print.support;

import com.xsy.scm.common.json.ScmOffsetDateTimeSerializer;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * 打印里的数量与金额保留四位小数；空值输出空字符串，不当作零。
 */
public final class ScmPrintText {

    private ScmPrintText() {
    }

    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 日期时间按北京时间输出 {@code yyyy-MM-dd HH:mm:ss}：直接 String.valueOf 会印出 ISO-8601 的毫秒和时区后缀，纸面上既难读也没意义。
     */
    public static String dateTime(OffsetDateTime value) {
        return value == null
            ? ""
            : value.atZoneSameInstant(ScmOffsetDateTimeSerializer.DISPLAY_ZONE)
                .format(ScmOffsetDateTimeSerializer.FORMATTER);
    }

    public static String fixed(BigDecimal value) {
        return value == null
            ? ""
            : value.setScale(ScmFixedScale4Serializer.SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
