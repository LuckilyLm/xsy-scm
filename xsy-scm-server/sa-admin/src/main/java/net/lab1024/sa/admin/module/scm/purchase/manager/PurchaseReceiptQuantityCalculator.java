package net.lab1024.sa.admin.module.scm.purchase.manager;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseConfigKey;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_QUANTITY_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_TOLERANCE_CONFIG_INVALID;

/**
 * 收货数量与对账恒等式（W5 Target Design §7.5 / §4.3 / P24）。
 *
 * <p><b>容差配置解析是一个纯函数</b>（{@link #tolerance(String)}），
 * 因此「缺失回退 10 / 非法 / 越界」都能被单测直接覆盖，不需要 Spring 或 DB。
 * 载体是 SmartAdmin 原生 Config（{@code t_config}），key 见 {@link PurchaseConfigKey}。
 *
 * <pre>
 * tolerance  = config("scm.purchase.over_receipt_tolerance_percent")  默认 10，范围 0–100
 * ceiling    = planned_quantity × (1 + tolerance / 100)
 * available  = ceiling − purchase_order_item.received_quantity
 * 本次 effectiveQuantity &gt; available → PURCHASE_RECEIPT_OVER_RECEIVED（整笔回滚）
 *
 * remaining_quantity    = GREATEST(planned − cumulative, 0)
 * over_receipt_quantity = GREATEST(cumulative − planned, 0)
 * receipt_difference    = cumulative − planned        （可为负）
 * </pre>
 *
 * <p><b>为什么 ceiling 不做 4 位取整</b>：它是**运行时**判定上限，不落库。
 * 保留精确值可以避免「取整后恰好放行/拒绝」的边界歧义；参与比较的
 * {@code effectiveQuantity} 本身已是 4 位定点。
 */
public final class PurchaseReceiptQuantityCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PurchaseReceiptQuantityCalculator() {
    }

    /**
     * 解析超收容差百分比。
     *
     * <p>解析口径是 **trim + {@link Integer#parseInt(String)}**，因此：
     * <ul>
     *   <li>首尾空白被接受（{@code "  15 "} → 15）；</li>
     *   <li>前导 {@code +} 被接受（{@code "+10"} → 10）—— 这是**有意为之的宽松**：
     *       值由管理员在配置页手工输入，数值语义无歧义，没有理由因写法而拒绝；</li>
     *   <li>小数、科学计数、百分号、千分位一律拒绝（{@code "10.5"} / {@code "1e2"} / {@code "10%"} → 40999）——
     *       它们代表「作者以为可以填别的单位」，静默截断会掩盖真实意图。</li>
     * </ul>
     *
     * @param raw {@code t_config} 里读到的原始值；{@code null} 或空白 → **回退默认 10**
     * @return 0–100（含端点）
     * @throws ScmBusinessException 非整数 / 负数 / &gt; 100 → 40999
     */
    public static int tolerance(String raw) {
        if (raw == null || raw.isBlank()) {
            // 配置缺失必须可回退：否则「未配置环境」完全无法收货
            return Integer.parseInt(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_DEFAULT);
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ScmBusinessException(PURCHASE_TOLERANCE_CONFIG_INVALID);
        }
        if (value < PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_MIN
                || value > PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_MAX) {
            throw new ScmBusinessException(PURCHASE_TOLERANCE_CONFIG_INVALID);
        }
        return value;
    }

    /** 可收上限 = planned × (1 + tolerance/100)，不取整（见类注释）。 */
    public static BigDecimal ceiling(BigDecimal plannedQuantity, int tolerancePercent) {
        BigDecimal factor = BigDecimal.ONE.add(
                BigDecimal.valueOf(tolerancePercent).divide(HUNDRED, 4, RoundingMode.HALF_UP));
        return plannedQuantity.multiply(factor);
    }

    /** 本次可收数量 = ceiling − 该采购行已累计收到的数量。 */
    public static BigDecimal available(BigDecimal ceiling, BigDecimal alreadyReceivedQuantity) {
        return ceiling.subtract(alreadyReceivedQuantity == null ? BigDecimal.ZERO : alreadyReceivedQuantity);
    }

    /** 剩余可收 = GREATEST(planned − cumulative, 0)。 */
    public static BigDecimal remaining(BigDecimal plannedQuantity, BigDecimal cumulativeReceivedQuantity) {
        return plannedQuantity.subtract(cumulativeReceivedQuantity).max(BigDecimal.ZERO);
    }

    /** 超收量 = GREATEST(cumulative − planned, 0)。 */
    public static BigDecimal overReceipt(BigDecimal plannedQuantity, BigDecimal cumulativeReceivedQuantity) {
        return cumulativeReceivedQuantity.subtract(plannedQuantity).max(BigDecimal.ZERO);
    }

    /** 收货差异 = cumulative − planned（**可为负**）。 */
    public static BigDecimal difference(BigDecimal plannedQuantity, BigDecimal cumulativeReceivedQuantity) {
        return cumulativeReceivedQuantity.subtract(plannedQuantity);
    }

    /**
     * 解析收货数量 / 实重（4 位定点字符串，必须 &gt; 0）。
     *
     * <p><b>为什么不复用 {@code PurchaseOrderValidator.decimal}</b>：那个方法的失败码是
     * {@code PURCHASE_QUANTITY_INVALID(40080)}（采购数量），而收货侧的错误码契约是
     * {@code PURCHASE_RECEIPT_QUANTITY_INVALID(40083)}（§7.7）。两个码必须各归其位 ——
     * 否则前端无法区分「建单时数量写错」与「收货时数量写错」。
     *
     * @throws ScmBusinessException 形态不符或 &le; 0（40083）
     */
    public static BigDecimal declared(String value) {
        if (value == null || !value.matches("[0-9]{1,14}\\.[0-9]{4}")) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_QUANTITY_INVALID);
        }
        BigDecimal parsed = new BigDecimal(value);
        if (parsed.signum() <= 0) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_QUANTITY_INVALID);
        }
        return parsed;
    }

    /**
     * 本次有效数量（§4.3 第 6 步 / §7.5 标品 vs 非标品）。
     *
     * <pre>
     * STANDARD      有效数量 = declaredQuantity；actualWeight / weighingSource / correctionReason 必须全空
     * NON_STANDARD  有效数量 = actualWeight（必填且 &gt; 0）；weighingSource 必须 == MANUAL
     * </pre>
     *
     * <p>{@code planned_quantity} 永不被覆盖（P22）。
     *
     * @throws ScmBusinessException 形态不符（40083）
     */
    public static BigDecimal effectiveQuantity(String productType,
                                              BigDecimal declaredQuantity,
                                              BigDecimal actualWeight,
                                              String weighingSource,
                                              String correctionReason) {
        if ("STANDARD".equals(productType)) {
            if (actualWeight != null || weighingSource != null || correctionReason != null) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_QUANTITY_INVALID);
            }
            if (declaredQuantity == null || declaredQuantity.signum() <= 0) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_QUANTITY_INVALID);
            }
            return declaredQuantity;
        }
        if ("NON_STANDARD".equals(productType)) {
            if (actualWeight == null || actualWeight.signum() <= 0) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_QUANTITY_INVALID);
            }
            if (!"MANUAL".equals(weighingSource)) {
                throw new ScmBusinessException(PURCHASE_RECEIPT_QUANTITY_INVALID);
            }
            return actualWeight;
        }
        throw new ScmBusinessException(PURCHASE_RECEIPT_QUANTITY_INVALID);
    }
}
