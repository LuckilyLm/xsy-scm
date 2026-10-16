package com.xsy.scm.print.support;

import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 打印数量与金额沿用后端四位定点口径，空值输出空字符串，不混同于零。 */
public final class ScmPrintText {

    private ScmPrintText() {
    }

    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String fixed(BigDecimal value) {
        return value == null
                ? ""
                : value.setScale(ScmFixedScale4Serializer.SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
