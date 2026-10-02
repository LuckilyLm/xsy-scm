package com.xsy.scm.print.support;

import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 打印文本格式化。
 *
 * <p>
 * 只做「值 → 字符串」这一件事，且与全仓口径一致：数量与金额是四位定点字符串
 * （{@link ScmFixedScale4Serializer#SCALE}，{@code HALF_UP}），<b>空值渲染为空串而不是 0</b> ——
 * 打印纸上「没有这个数」和「这个数是零」必须能分辨。
 */
public final class ScmPrintText {

    private ScmPrintText() {
    }

    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String fixed(BigDecimal value) {
        return value == null ? "" : value.setScale(ScmFixedScale4Serializer.SCALE, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
