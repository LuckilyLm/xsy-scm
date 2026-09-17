package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseAmountCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 采购金额精度契约测试（W5 Target Design §11.1，8 例）。
 *
 * <p>口径与 W4 的 {@code OrderAmountCalculator} 一致：**4 位定点、HALF_UP、null 传播**。
 * 这里锁死三件事：
 * <ol>
 *   <li>{@code null} 参与运算 → 结果仍为 {@code null}（不静默变 {@code 0.0000}）；</li>
 *   <li>{@code 0.0000} 是**合法值**，与 {@code null} 语义不同；</li>
 *   <li>{@code NUMERIC(18,4)} 的容量上界（整数 14 位）越界 → 40081。</li>
 * </ol>
 */
class PurchaseAmountCalculatorTest {

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    @Test
    @DisplayName("line_amount = planned × price，4 位 HALF_UP")
    void lineAmountRoundsHalfUpToScale4() {
        assertThat(PurchaseAmountCalculator.lineAmount(new BigDecimal("3.0000"), new BigDecimal("2.5000")))
                .isEqualByComparingTo("7.5000");
        // 1.0005 × 0.1000 = 0.10005 → HALF_UP → 0.1001
        assertThat(PurchaseAmountCalculator.lineAmount(new BigDecimal("1.0005"), new BigDecimal("0.1000")))
                .isEqualByComparingTo("0.1001");
        assertThat(PurchaseAmountCalculator.lineAmount(new BigDecimal("1.0000"), new BigDecimal("0.1000")).scale())
                .isEqualTo(PurchaseAmountCalculator.SCALE);
    }

    @Test
    @DisplayName("单价为 null → 行金额为 null（不落 0.0000）")
    void nullPriceYieldsNullLineAmount() {
        assertThat(PurchaseAmountCalculator.lineAmount(new BigDecimal("10.0000"), null)).isNull();
        assertThat(PurchaseAmountCalculator.bounded(null)).isNull();
    }

    @Test
    @DisplayName("单价 0.0000 是合法值 → 行金额 0.0000（≠ null）")
    void zeroPriceIsLegalAndDistinctFromNull() {
        BigDecimal zero = PurchaseAmountCalculator.lineAmount(new BigDecimal("3.0000"), BigDecimal.ZERO);
        assertThat(zero).isNotNull();
        assertThat(zero).isEqualByComparingTo("0.0000");
        assertThat(zero).isNotEqualTo(PurchaseAmountCalculator.lineAmount(new BigDecimal("3.0000"), null));
    }

    @Test
    @DisplayName("空行集合 → 单头金额 0.0000（草稿单可以有零行金额语义）")
    void emptyLinesYieldZeroTotal() {
        assertThat(PurchaseAmountCalculator.totalAmount(Collections.emptyList()))
                .isEqualByComparingTo("0.0000");
        assertThat(PurchaseAmountCalculator.totalAmount(null)).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("任一行金额为 null → 单头金额为 null")
    void nullLinePoisonsTotal() {
        assertThat(PurchaseAmountCalculator.totalAmount(Arrays.asList(BigDecimal.ONE, null))).isNull();
        assertThat(PurchaseAmountCalculator.totalAmount(Collections.singletonList((BigDecimal) null))).isNull();
    }

    @Test
    @DisplayName("total_amount = Σ line_amount，结果同样规范化到 4 位")
    void totalSumsAndNormalizes() {
        List<BigDecimal> lines = List.of(
                new BigDecimal("7.5000"),
                new BigDecimal("0.0000"),
                new BigDecimal("12.3456"));
        BigDecimal total = PurchaseAmountCalculator.totalAmount(lines);
        assertThat(total).isEqualByComparingTo("19.8456");
        assertThat(total.scale()).isEqualTo(PurchaseAmountCalculator.SCALE);
    }

    @Test
    @DisplayName("容量边界：99999999999999.9999 通过，再大 1 分越界 → 40081")
    void capacityBoundary() {
        assertThatCode(() -> PurchaseAmountCalculator.bounded(new BigDecimal("99999999999999.9999")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PurchaseAmountCalculator.bounded(new BigDecimal("100000000000000.0000")))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40081));
    }

    @Test
    @DisplayName("行金额溢出同样抛 40081（不是 NumberFormatException）")
    void lineAmountOverflowThrowsPriceInvalid() {
        assertThatThrownBy(() -> PurchaseAmountCalculator.lineAmount(
                new BigDecimal("99999999999999.9999"), BigDecimal.TEN))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40081));
        assertThatThrownBy(() -> PurchaseAmountCalculator.totalAmount(List.of(
                new BigDecimal("99999999999999.9999"), new BigDecimal("1.0000"))))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40081));
    }
}
