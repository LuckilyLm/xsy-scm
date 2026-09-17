package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 收货重量口径（W5 Target Design §7.5 / §4.3 第 6 步，4 例）。
 *
 * <pre>
 * STANDARD      有效数量 = declaredQuantity；actualWeight / weighingSource / correctionReason 必须全空
 * NON_STANDARD  有效数量 = actualWeight（必填 &gt; 0）；weighingSource 必须 == MANUAL
 * </pre>
 *
 * <p><b>为什么非标品的有效数量取实重而不是声明数量</b>：鲜蔬是称重商品，
 * 采购员下单时写的数量只是意向，真正决定库存与结算的是**过秤读数**。
 * 如果取声明数量，电子秤接进来（W6）以后就会出现「称了但没用上」的静默错误 ——
 * 这里用「声明 5 / 实重 3 → 累计 3」直接把这个口径钉死。
 *
 * <p><b>实重必须留痕</b>：每次带实重的确认都要往 `receipt_weighing_record` 追一行审计事实
 * （只追加，无 version / deleted），原始读数与确认读数同值（G-05 手工录入）。
 */
@DisplayName("收货重量：非标品实重口径与过秤留痕（PG IT）")
class PurchaseReceiptWeightIT extends ScmW5PgITBase {

    /** 用「声明量 + 实重」分开给的方式确认收货。 */
    private PurchaseReceiptVO confirm(ReceiptFixture fx, String declared, String actualWeight) {
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        return purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), declared, actualWeight)),
                prefix + ":weight:" + current.getId() + ":" + declared + ":" + actualWeight);
    }

    private Map<String, Object> latestWeighingRecord(Long receiptItemId) {
        return jdbc.queryForMap(
                "SELECT purchase_receipt_item_id, raw_reading, confirmed_reading, unit, source, "
                        + "modification_reason, operator FROM receipt_weighing_record "
                        + "WHERE purchase_receipt_item_id = ? ORDER BY id DESC LIMIT 1", receiptItemId);
    }

    private int weighingRecordCount(Long receiptItemId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM receipt_weighing_record WHERE purchase_receipt_item_id = ?",
                Integer.class, receiptItemId);
    }

    // ------------------------------------------------------------------
    // 1. 实重优先
    // ------------------------------------------------------------------

    @Test
    @DisplayName("非标品：有效数量取实重（声明 5.0000 / 实重 3.0000 → 累计 3.0000）")
    void nonStandardEffectiveQuantityComesFromActualWeight() {
        ReceiptFixture fx = receiptFixture("WG1", "10.0000");
        PurchaseReceiptVO confirmed = confirm(fx, "5.0000", "3.0000");

        PurchaseReceiptItemVO line = confirmed.getItems().getFirst();
        assertThat(line.getActualWeight()).isEqualByComparingTo("3.0000");
        assertThat(line.getWeighingSource()).isEqualTo("MANUAL");
        assertThat(line.getWeightUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
        // 本次有效数量 = 实重，不是声明的 5.0000
        assertThat(line.getReceivedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("3.0000");

        // 采购行的已收量同样只累计实重
        assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                .isEqualByComparingTo("3.0000");
        // 采购单未收齐 → PARTIALLY_RECEIVED
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("PARTIALLY_RECEIVED");
    }

    // ------------------------------------------------------------------
    // 2. 非标品缺实重
    // ------------------------------------------------------------------

    @Test
    @DisplayName("非标品：缺实重 → 40083，且不写任何过秤记录、采购行不动")
    void nonStandardRequiresActualWeight() {
        ReceiptFixture fx = receiptFixture("WG2", "10.0000");

        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        expectCode(() -> purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "3.0000", null)),
                prefix + ":WG2:noWeight"), 40083);

        assertThat(weighingRecordCount(line.getId())).isZero();
        assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                .isEqualByComparingTo("0.0000");
        assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("DRAFT");
    }

    // ------------------------------------------------------------------
    // 3. 过秤留痕
    // ------------------------------------------------------------------

    @Test
    @DisplayName("带实重的确认追加一行过秤记录：原始读数 = 确认读数、来源 MANUAL、单位随采购单位")
    void weighingRecordIsAppendedForWeightedReceipt() {
        ReceiptFixture fx = receiptFixture("WG3", "10.0000");
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        Long receiptItemId = current.getItems().getFirst().getId();
        assertThat(weighingRecordCount(receiptItemId)).isZero();

        confirm(fx, "4.0000", "4.0000");

        assertThat(weighingRecordCount(receiptItemId)).isEqualTo(1);
        Map<String, Object> record = latestWeighingRecord(receiptItemId);
        assertThat(record.get("purchase_receipt_item_id")).isEqualTo(receiptItemId);
        assertThat(record.get("raw_reading")).isEqualTo(new java.math.BigDecimal("4.0000"));
        assertThat(record.get("confirmed_reading")).isEqualTo(new java.math.BigDecimal("4.0000"));
        assertThat(record.get("unit")).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(record.get("source")).isEqualTo("MANUAL");
        assertThat(record.get("modification_reason")).isNull();
        // 操作者落的是 `userType:employeeId`（ScmOperator 的口径），不是员工姓名
        assertThat(record.get("operator")).isEqualTo(ScmOperator.current());
    }

    // ------------------------------------------------------------------
    // 4. 声明量形态
    // ------------------------------------------------------------------

    @Test
    @DisplayName("声明数量 / 实重必须是 4 位定点正数：形态不符 → 40083（不是采购侧的 40080）")
    void declaredShapeIsValidatedWithReceiptCode() {
        ReceiptFixture fx = receiptFixture("WG4", "10.0000");
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();

        // 两位小数、缺小数位、负数、0 一律拒绝
        for (String bad : new String[]{"3.00", "3", "0.0000", "-3.0000"}) {
            expectCode(() -> purchaseReceiptService.confirm(
                    confirmForm(current.getId(), current.getVersion(),
                            receiptLine(line.getId(), line.getVersion(), bad, "3.0000")),
                    prefix + ":WG4:" + bad), 40083);
        }

        // 实重形态同样校验
        expectCode(() -> purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "3.0000", "3.0")),
                prefix + ":WG4:weight"), 40083);

        assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("DRAFT");
        assertThat(weighingRecordCount(line.getId())).isZero();
    }
}
