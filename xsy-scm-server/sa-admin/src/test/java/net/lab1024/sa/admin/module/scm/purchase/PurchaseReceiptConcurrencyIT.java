package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 收货累计与乐观锁（W5 Target Design §11.2，3 例）。
 *
 * <p><b>`purchase_order_item.received_quantity` 只增不减</b>，唯一入口是
 * {@code accumulateReceived}（持行锁的 `UPDATE ... SET received_quantity = received_quantity + ?`）。
 * 分次到货因此是「多次确认、同一行累加」，而不是「覆盖上一次的值」。
 *
 * <p><b>收货单只记「本次」，采购行记「累计」</b>：第二张收货单的
 * {@code received_quantity} 是本次数量、{@code cumulative_received_quantity} 是含历史的累计 ——
 * 把这两个混为一谈会让「分次到货」的账面完全错位。
 *
 * <p><b>两层版本各自独立</b>：收货单版本挡「同一张草稿被两个人同时确认」，
 * 收货行版本挡「同一行被两次并发确认」。第 2、3 例分别验证两层。
 */
@DisplayName("收货累计与乐观锁：累加 / 单据版本 / 行版本（PG IT）")
class PurchaseReceiptConcurrencyIT extends ScmW5PgITBase {

    private PurchaseReceiptVO confirm(PurchaseReceiptVO receipt, String quantity, String suffix) {
        PurchaseReceiptItemVO line = receipt.getItems().getFirst();
        return purchaseReceiptService.confirm(
                confirmForm(receipt.getId(), receipt.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), quantity)),
                prefix + ":conc:" + suffix);
    }

    // ------------------------------------------------------------------
    // 1. 分次到货累加
    // ------------------------------------------------------------------

    @Test
    @DisplayName("两次收货在同一采购行累加：6.0000 + 3.0000 = 9.0000")
    void twoReceiptsAccumulateOnTheSameOrderItem() {
        ReceiptFixture fx = receiptFixture("CC1", "10.0000");

        confirm(fx.receipt(), "6.0000", "a");
        assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                .isEqualByComparingTo("6.0000");

        PurchaseReceiptVO second = createAnotherReceipt(fx.order().getId(), "b");
        confirm(second, "3.0000", "b");

        assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                .isEqualByComparingTo("9.0000");

        // 第二张单：本次 3.0000，累计 9.0000，还差 1.0000
        PurchaseReceiptItemVO line = reloadReceipt(second.getId()).getItems().getFirst();
        assertThat(line.getReceivedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("9.0000");
        assertThat(line.getRemainingQuantity()).isEqualByComparingTo("1.0000");
        assertThat(line.getReceiptDifference()).isEqualByComparingTo("-1.0000");

        // 第一张单的历史值不被第二次确认改写
        PurchaseReceiptItemVO firstLine = reloadReceipt(fx.receipt().getId()).getItems().getFirst();
        assertThat(firstLine.getReceivedQuantity()).isEqualByComparingTo("6.0000");
        assertThat(firstLine.getCumulativeReceivedQuantity()).isEqualByComparingTo("6.0000");
    }

    // ------------------------------------------------------------------
    // 2. 收货单版本
    // ------------------------------------------------------------------

    @Test
    @DisplayName("收货单版本过期 → 40921，单据保持草稿、采购行不动")
    void staleReceiptVersionRejected() {
        ReceiptFixture fx = receiptFixture("CC2", "10.0000");
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();

        expectCode(() -> purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion() + 5,
                        receiptLine(line.getId(), line.getVersion(), "5.0000")),
                prefix + ":CC2:stale"), 40921);

        assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("DRAFT");
        assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                .isEqualByComparingTo("0.0000");
    }

    // ------------------------------------------------------------------
    // 3. 收货行版本
    // ------------------------------------------------------------------

    @Test
    @DisplayName("收货行版本过期 → 40921（单据版本合法也拦得住）")
    void staleReceiptItemVersionRejected() {
        ReceiptFixture fx = receiptFixture("CC3", "10.0000");
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();

        // 单据版本是当前值，只有行版本过期
        expectCode(() -> purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion() + 5, "5.0000")),
                prefix + ":CC3:stale"), 40921);

        assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("DRAFT");
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("SUBMITTED");

        // 正确版本仍可确认
        PurchaseReceiptVO confirmed = confirm(current, "5.0000", "ok");
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
    }
}
