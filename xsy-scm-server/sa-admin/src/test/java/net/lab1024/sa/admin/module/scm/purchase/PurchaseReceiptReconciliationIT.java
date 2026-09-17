package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 收货对账恒等式（W5 Target Design §7.6 / P24，3 例）。
 *
 * <pre>
 * remaining_quantity    = GREATEST(planned − cumulative, 0)   ← 永不为负
 * over_receipt_quantity = GREATEST(cumulative − planned, 0)   ← 永不为负
 * receipt_difference    = cumulative − planned                ← **可为负**
 * </pre>
 *
 * <p><b>为什么 `difference` 允许为负而另两个不允许</b>：前两个是**可执行的量**
 * （还能收多少 / 已经超了多少），负数没有业务含义；`difference` 是**对账差异**，
 * 它的符号本身就是信息 —— 负 = 欠收、正 = 超收、零 = 收齐。
 * 把三者压成一个字段会立刻丢掉「欠收」与「超收」的区分，所以必须分开存。
 *
 * <p>三个量都由 {@code PurchaseReceiptQuantityCalculator} 的纯函数计算，
 * 并且 V15 的 {@code ck_purchase_receipt_item_reconciliation} 在库层复核同一组恒等式 ——
 * 本类除了断言 Java 侧，还逐行回查库层是否与 Java 侧完全一致（防止「写库时算了一套、
 * 读出来又算了一套」）。
 */
@DisplayName("收货对账恒等式：remaining / over / difference（PG IT）")
class PurchaseReceiptReconciliationIT extends ScmW5PgITBase {

    private PurchaseReceiptVO confirm(ReceiptFixture fx, String quantity) {
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        return purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), quantity)),
                prefix + ":rec:" + current.getId() + ":" + quantity);
    }

    /** 库层的三个对账量（与 Java 侧逐字段比对，防止两套口径）。 */
    private void assertDatabaseMatchesJava(Long receiptItemId, PurchaseReceiptItemVO line) {
        var row = jdbc.queryForMap(
                "SELECT received_quantity, cumulative_received_quantity, remaining_quantity, "
                        + "over_receipt_quantity, receipt_difference "
                        + "FROM purchase_receipt_item WHERE id = ?", receiptItemId);
        assertThat((BigDecimal) row.get("received_quantity")).isEqualByComparingTo(line.getReceivedQuantity());
        assertThat((BigDecimal) row.get("cumulative_received_quantity"))
                .isEqualByComparingTo(line.getCumulativeReceivedQuantity());
        assertThat((BigDecimal) row.get("remaining_quantity")).isEqualByComparingTo(line.getRemainingQuantity());
        assertThat((BigDecimal) row.get("over_receipt_quantity"))
                .isEqualByComparingTo(line.getOverReceiptQuantity());
        assertThat((BigDecimal) row.get("receipt_difference")).isEqualByComparingTo(line.getReceiptDifference());

        // 恒等式本身（不依赖具体数值）
        BigDecimal planned = line.getPlannedQuantity();
        BigDecimal cumulative = line.getCumulativeReceivedQuantity();
        assertThat(line.getRemainingQuantity()).isEqualByComparingTo(
                planned.subtract(cumulative).max(BigDecimal.ZERO));
        assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo(
                cumulative.subtract(planned).max(BigDecimal.ZERO));
        assertThat(line.getReceiptDifference()).isEqualByComparingTo(cumulative.subtract(planned));
        // 两个非负量 + 差异的符号一致性
        assertThat(line.getRemainingQuantity()).isNotNegative();
        assertThat(line.getOverReceiptQuantity()).isNotNegative();
    }

    // ------------------------------------------------------------------
    // 1. 欠收
    // ------------------------------------------------------------------

    @Test
    @DisplayName("欠收：remaining > 0、over = 0、difference < 0（planned 10 收 4）")
    void underReceiptKeepsRemainingAndNegativeDifference() {
        ReceiptFixture fx = receiptFixture("P24A", "10.0000");
        PurchaseReceiptVO confirmed = confirm(fx, "4.0000");
        PurchaseReceiptItemVO line = confirmed.getItems().getFirst();

        assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("4.0000");
        assertThat(line.getRemainingQuantity()).isEqualByComparingTo("6.0000");
        assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
        assertThat(line.getReceiptDifference()).isEqualByComparingTo("-6.0000");
        assertDatabaseMatchesJava(line.getId(), line);

        // 采购单进入 PARTIALLY_RECEIVED，且采购行 remaining 同步
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getRemainingQuantity())
                .isEqualByComparingTo("6.0000");
    }

    // ------------------------------------------------------------------
    // 2. 收齐
    // ------------------------------------------------------------------

    @Test
    @DisplayName("收齐：三个量全部为 0，采购单 → RECEIVED")
    void exactReceiptZeroesEveryReconciliationField() {
        ReceiptFixture fx = receiptFixture("P24B", "10.0000");
        PurchaseReceiptVO confirmed = confirm(fx, "10.0000");
        PurchaseReceiptItemVO line = confirmed.getItems().getFirst();

        assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("10.0000");
        assertThat(line.getRemainingQuantity()).isEqualByComparingTo("0.0000");
        assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
        assertThat(line.getReceiptDifference()).isEqualByComparingTo("0.0000");
        assertDatabaseMatchesJava(line.getId(), line);

        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");
    }

    // ------------------------------------------------------------------
    // 3. 超收（容差内）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("超收：remaining = 0、over > 0、difference > 0（planned 10 收 11，容差 10%）")
    void overReceiptWithinToleranceRecordsPositiveOverAndDifference() {
        ReceiptFixture fx = receiptFixture("P24C", "10.0000");
        // ceiling = 10 × 1.1 = 11 → 11.0000 恰好在上限内
        PurchaseReceiptVO confirmed = confirm(fx, "11.0000");
        PurchaseReceiptItemVO line = confirmed.getItems().getFirst();

        assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("11.0000");
        assertThat(line.getRemainingQuantity()).isEqualByComparingTo("0.0000");
        assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo("1.0000");
        assertThat(line.getReceiptDifference()).isEqualByComparingTo("1.0000");
        assertDatabaseMatchesJava(line.getId(), line);

        // 全部行都 >= planned → RECEIVED（超收也是收齐）
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");
        assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getOverReceiptQuantity())
                .isEqualByComparingTo("1.0000");
    }
}
