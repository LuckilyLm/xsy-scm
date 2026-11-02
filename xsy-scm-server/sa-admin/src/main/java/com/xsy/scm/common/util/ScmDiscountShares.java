package com.xsy.scm.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 优惠分摊的公共算法：按行毛额比例把一笔已确认优惠拆到当前行。
 *
 * <p>
 * 少发、超发、退货与纸面打印必须共用同一公式，否则应收、送货单和红字会各自算出不同净额。 结果始终夹在 {@code [0, 当前行毛额]}，避免历史数据或异常比例把优惠放大。
 */
public final class ScmDiscountShares {

    private static final int SCALE = 4;

    private ScmDiscountShares() {
    }

    public static BigDecimal proratedShare(BigDecimal gross, BigDecimal orderedGross, BigDecimal allocated) {
        if (gross == null || gross.signum() <= 0 || orderedGross == null || orderedGross.signum() <= 0
            || allocated == null || allocated.signum() <= 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return gross.multiply(allocated).divide(orderedGross, SCALE, RoundingMode.HALF_UP).min(gross)
            .max(BigDecimal.ZERO);
    }
}
