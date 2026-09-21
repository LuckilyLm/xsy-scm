package net.lab1024.sa.admin.module.scm.purchase.manager;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_PRICE_INVALID;

/**
 * 采购金额计算（W5 Target Design §7.5 P6）。
 *
 * <pre>
 * line_amount  = round(planned_quantity × purchase_price, 4, HALF_UP)
 * total_amount = Σ line_amount（create / update 时重算）
 * </pre>
 *
 * <p>收货**不改变** {@code total_amount}：W5 没有「实际金额」字段（A-D8）。
 * 与 W4 的 {@code OrderAmountCalculator} 同口径：{@code null} 参与运算时结果保持 {@code null}，
 * 不把「无值」静默变成 {@code 0.0000}。
 */
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
    public static BigDecimal bounded(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.setScale(SCALE, RoundingMode.HALF_UP);
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
