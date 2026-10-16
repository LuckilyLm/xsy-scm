package com.xsy.scm.purchase.manager;

import com.xsy.scm.common.exception.ScmBusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_PRICE_INVALID;

/** 采购行金额按计划数量乘采购价计算，逐行 HALF_UP 保留四位小数，再合计行金额。不要先汇总数量再统一计算，以免尾差。 */
public final class PurchaseAmountCalculator {

    public static final int SCALE = 4;

    /**
     * 与 {@code NUMERIC(18,4)} 的容量一致：整数部分 14 位。
     */
    private static final BigDecimal MAX = new BigDecimal("99999999999999.9999");

    private PurchaseAmountCalculator() {
    }

    /**
     * 规范化到 4 位小数并做容量校验；{@code null} 透传。溢出 → 40081。
     */
    public static BigDecimal bounded(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        BigDecimal normalized = amount.setScale(SCALE, RoundingMode.HALF_UP);
        if (normalized.abs().compareTo(MAX) > 0) {
            throw new ScmBusinessException(PURCHASE_PRICE_INVALID);
        }
        return normalized;
    }

    /**
     * 行金额；单价为 {@code null} 时返回 {@code null}（不是 0）。
     */
    public static BigDecimal lineAmount(BigDecimal plannedQuantity, BigDecimal purchasePrice) {
        return purchasePrice == null ? null : bounded(plannedQuantity.multiply(purchasePrice));
    }

    /**
     * 单头金额；任一行金额为 {@code null} 时返回 {@code null}。
     */
    public static BigDecimal totalAmount(List<BigDecimal> lineAmounts) {
        if (lineAmounts == null || lineAmounts.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (lineAmounts.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        return bounded(lineAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
