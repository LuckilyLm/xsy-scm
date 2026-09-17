package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseConfigKey;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseAmountCalculator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseReceiptQuantityCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 收货数量与对账恒等式契约测试（W5 Target Design §11.1，16 例）。
 *
 * <p>本测试是 **Q3a 与 P24 的单点防线**：
 * <ul>
 *   <li>容差解析 {@link PurchaseReceiptQuantityCalculator#tolerance(String)} 是纯函数，
 *       因此「缺失回退 10 / 非法 / 越界 → 40999」无需 Spring 与 DB 即可断言；</li>
 *   <li>对账恒等式（remaining / over / difference）在这里锁死符号方向 ——
 *       `difference` **可为负**，这正是 {@code ck_purchase_receipt_item_reconciliation} 的前提。</li>
 * </ul>
 */
class PurchaseReceiptQuantityCalculatorTest {

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    private static BigDecimal q(String value) {
        return new BigDecimal(value);
    }

    // ------------------------------------------------------------------
    // Q3a：容差配置解析（缺失回退 / 合法 / 非法 / 越界）
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "raw=[{0}] -> {1}")
    @CsvSource({
            "10,10",
            "0,0",
            "100,100",
            "'  15 ',15",
            "'007',7"
    })
    @DisplayName("Q3a：合法容差原样解析（含首尾空白与补零）")
    void toleranceParsesLegalValues(String raw, int expected) {
        assertThat(PurchaseReceiptQuantityCalculator.tolerance(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Q3a：配置缺失（null / 空白）回退默认 10 —— 未配置环境必须仍能收货")
    void toleranceFallsBackToDefaultWhenMissing() {
        int fallback = Integer.parseInt(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_DEFAULT);
        assertThat(fallback).isEqualTo(10);
        assertThat(PurchaseReceiptQuantityCalculator.tolerance(null)).isEqualTo(fallback);
        assertThat(PurchaseReceiptQuantityCalculator.tolerance("")).isEqualTo(fallback);
        assertThat(PurchaseReceiptQuantityCalculator.tolerance("   ")).isEqualTo(fallback);
    }

    @ParameterizedTest(name = "raw=[{0}]")
    @ValueSource(strings = {"-1", "101", "abc", "10.5", "1e2", "10%", "+ 10", "99999999999999999999"})
    @DisplayName("Q3a：非整数 / 越界 / 溢出 → 40999（绝不静默回退）")
    void toleranceRejectsIllegalValues(String raw) {
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.tolerance(raw))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40999));
    }

    @Test
    @DisplayName("Q3a：解析口径是「trim + Integer.parseInt」——前导 + 与首尾空白被接受，这是有意为之的宽松")
    void toleranceParsingIsIntentionallyLenientAboutSignAndWhitespace() {
        // Integer.parseInt 接受前导 '+'，因此 "+10" 视为合法整数 10（不是非法值）
        assertThat(PurchaseReceiptQuantityCalculator.tolerance("+10")).isEqualTo(10);
        assertThat(PurchaseReceiptQuantityCalculator.tolerance("\t10\n")).isEqualTo(10);
    }

    @Test
    @DisplayName("Q3a：范围端点 0 与 100 是闭区间（不越界）")
    void toleranceRangeIsInclusive() {
        assertThat(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_MIN).isZero();
        assertThat(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_MAX).isEqualTo(100);
        assertThat(PurchaseReceiptQuantityCalculator.tolerance("0")).isZero();
        assertThat(PurchaseReceiptQuantityCalculator.tolerance("100")).isEqualTo(100);
    }

    // ------------------------------------------------------------------
    // ceiling / available
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ceiling = planned × (1 + tolerance/100)，容差 0 / 10 / 100")
    void ceilingScalesByTolerance() {
        assertThat(PurchaseReceiptQuantityCalculator.ceiling(q("100.0000"), 0)).isEqualByComparingTo("100.0000");
        assertThat(PurchaseReceiptQuantityCalculator.ceiling(q("100.0000"), 10)).isEqualByComparingTo("110.0000");
        assertThat(PurchaseReceiptQuantityCalculator.ceiling(q("100.0000"), 100)).isEqualByComparingTo("200.0000");
    }

    @Test
    @DisplayName("ceiling 不做 4 位取整：小数计划量保持精确（避免边界放行歧义）")
    void ceilingKeepsExactPrecision() {
        BigDecimal ceiling = PurchaseReceiptQuantityCalculator.ceiling(q("3.3333"), 10);
        assertThat(ceiling).isEqualByComparingTo("3.66663");
        assertThat(ceiling.scale()).isGreaterThan(PurchaseAmountCalculator.SCALE);
    }

    @Test
    @DisplayName("available = ceiling − 已累计收货；未收过时已收量按 0 处理")
    void availableSubtractsCumulative() {
        assertThat(PurchaseReceiptQuantityCalculator.available(q("110.0000"), q("10.0000")))
                .isEqualByComparingTo("100.0000");
        assertThat(PurchaseReceiptQuantityCalculator.available(q("110.0000"), null))
                .isEqualByComparingTo("110.0000");
        assertThat(PurchaseReceiptQuantityCalculator.available(q("110.0000"), q("110.0000")))
                .isEqualByComparingTo("0.0000");
    }

    // ------------------------------------------------------------------
    // P24 对账恒等式
    // ------------------------------------------------------------------

    @Test
    @DisplayName("P24：remaining = GREATEST(planned − cumulative, 0)")
    void remainingIsFlooredAtZero() {
        assertThat(PurchaseReceiptQuantityCalculator.remaining(q("100.0000"), q("40.0000")))
                .isEqualByComparingTo("60.0000");
        assertThat(PurchaseReceiptQuantityCalculator.remaining(q("100.0000"), q("100.0000")))
                .isEqualByComparingTo("0.0000");
        // 超收后剩余仍是 0，而不是负数
        assertThat(PurchaseReceiptQuantityCalculator.remaining(q("100.0000"), q("120.0000")))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("P24：over_receipt = GREATEST(cumulative − planned, 0)")
    void overReceiptIsFlooredAtZero() {
        assertThat(PurchaseReceiptQuantityCalculator.overReceipt(q("100.0000"), q("80.0000")))
                .isEqualByComparingTo("0.0000");
        assertThat(PurchaseReceiptQuantityCalculator.overReceipt(q("100.0000"), q("100.0000")))
                .isEqualByComparingTo("0.0000");
        assertThat(PurchaseReceiptQuantityCalculator.overReceipt(q("100.0000"), q("120.0000")))
                .isEqualByComparingTo("20.0000");
    }

    @Test
    @DisplayName("P24：receipt_difference = cumulative − planned，**可为负**")
    void differenceIsSigned() {
        assertThat(PurchaseReceiptQuantityCalculator.difference(q("100.0000"), q("80.0000")))
                .isEqualByComparingTo("-20.0000");
        assertThat(PurchaseReceiptQuantityCalculator.difference(q("100.0000"), q("100.0000")))
                .isEqualByComparingTo("0.0000");
        assertThat(PurchaseReceiptQuantityCalculator.difference(q("100.0000"), q("120.0000")))
                .isEqualByComparingTo("20.0000");
    }

    @Test
    @DisplayName("P24：三条恒等式同时成立（over − remaining == difference）")
    void identitiesAreConsistent() {
        BigDecimal planned = q("100.0000");
        for (String cumulative : new String[]{"0.0000", "37.5000", "100.0000", "120.2500"}) {
            BigDecimal c = q(cumulative);
            BigDecimal remaining = PurchaseReceiptQuantityCalculator.remaining(planned, c);
            BigDecimal over = PurchaseReceiptQuantityCalculator.overReceipt(planned, c);
            BigDecimal difference = PurchaseReceiptQuantityCalculator.difference(planned, c);
            // remaining = max(planned−cum,0)，over = max(cum−planned,0)，两者最多一个非零，
            // 且 over − remaining = cum − planned = difference
            assertThat(over.subtract(remaining)).as("cumulative=%s", cumulative).isEqualByComparingTo(difference);
            assertThat(remaining.signum() == 0 || over.signum() == 0)
                    .as("cumulative=%s：remaining 与 over 不可同时为正", cumulative).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // §4.3 第 6 步：标品 vs 非标品的有效数量
    // ------------------------------------------------------------------

    @Test
    @DisplayName("标品：有效数量 = declared_quantity；实重 / 称重来源 / 修正原因必须全空")
    void standardProductUsesDeclaredQuantity() {
        assertThat(PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "STANDARD", q("12.5000"), null, null, null)).isEqualByComparingTo("12.5000");
    }

    @Test
    @DisplayName("标品：带实重 / 称重来源 / 修正原因 → 40083（三字段同生同灭）")
    void standardProductRejectsWeightFields() {
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "STANDARD", q("12.5000"), q("12.5000"), null, null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "STANDARD", q("12.5000"), null, "MANUAL", null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "STANDARD", q("12.5000"), null, null, "更正"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
    }

    @Test
    @DisplayName("标品：declared 缺失或非正 → 40083")
    void standardProductRequiresPositiveDeclared() {
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "STANDARD", null, null, null, null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "STANDARD", q("0.0000"), null, null, null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
    }

    @Test
    @DisplayName("非标品：有效数量 = actual_weight（必填且 > 0），weighing_source 必须 MANUAL")
    void nonStandardProductUsesActualWeight() {
        assertThat(PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "NON_STANDARD", null, q("48.2000"), "MANUAL", null)).isEqualByComparingTo("48.2000");
    }

    @Test
    @DisplayName("非标品：实重缺失 / 非正 / DEVICE → 40083（DEVICE 是 G-05 扩展点，W5 不开放）")
    void nonStandardProductRejectsBadWeight() {
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "NON_STANDARD", null, null, "MANUAL", null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "NON_STANDARD", null, q("0.0000"), "MANUAL", null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "NON_STANDARD", null, q("48.2000"), "DEVICE", null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "NON_STANDARD", null, q("48.2000"), null, null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
    }

    @Test
    @DisplayName("未知商品类型 → 40083（不做「默认按标品」的静默兜底）")
    void unknownProductTypeRejected() {
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                "SEMI_STANDARD", q("1.0000"), null, null, null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
        assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                null, q("1.0000"), null, null, null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
    }

    // ------------------------------------------------------------------
    // 收货数量 / 实重的定点解析（错误码必须是 40083，不是采购侧的 40080）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("收货数量解析：4 位定点字符串原样通过，数值语义不变")
    void declaredAcceptsFourScaleStrings() {
        assertThat(PurchaseReceiptQuantityCalculator.declared("1000.0000")).isEqualByComparingTo("1000.0000");
        assertThat(PurchaseReceiptQuantityCalculator.declared("0.0001")).isEqualByComparingTo("0.0001");
        assertThat(PurchaseReceiptQuantityCalculator.declared("99999999999999.9999"))
                .isEqualByComparingTo("99999999999999.9999");
    }

    @Test
    @DisplayName("收货数量解析：缺失 / 小数位不符 / 负数 / 零 → 40083（不是采购侧的 40080）")
    void declaredRejectsBadShapeWithReceiptCode() {
        // 与 PurchaseOrderValidator.decimal 的差别只有失败码：40083 vs 40080。
        // 前端要能区分「建单时数量写错」与「收货时数量写错」，所以这里必须逐例锁死。
        for (String bad : new String[]{null, "", "1", "1.000", "1.00000", "-1.0000", "0.0000",
                "1e2", "1.0000%", " 1.0000"}) {
            assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.declared(bad))
                    .as("bad value: [%s]", bad)
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            e -> assertThat(codeOf(e)).isEqualTo(40083));
        }
    }
}
