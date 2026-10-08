package com.xsy.scm.finance;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddItemForm;
import com.xsy.scm.finance.domain.vo.FinancePaymentVO;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.service.FinancePaymentService;
import com.xsy.scm.finance.service.FinancePayableQueryService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.service.FinanceReceivableQueryService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结清状态派生口径（FIX-01，PG IT）。
 *
 * <p>被钉住的语义：完全红冲（净额为零）且无核销的单据是<b>已结清</b>而不是未结清——
 * 它没有任何待收 / 待付金额，判 OPEN 会让它永远挂在未结清列表里。
 * 超额核销（已核销额超过净额）是异常，必须保持 PARTIAL 并有 overAppliedAmount，
 * 不得被「未结净额为零」误标记成已结清。
 *
 * <p>每个场景都同时断言详情 VO 与列表筛选：状态、筛选结果、金额三者必须一致。
 */
@DisplayName("结清状态派生：全额红冲 / 部分红冲 / 部分核销 / 超额核销（FIX-01，PG IT）")
class ScmFinanceSettleStatePgIT extends ScmW5PgITBase {

    private static final OffsetDateTime EVENT_AT =
            OffsetDateTime.of(2026, 3, 17, 9, 0, 0, 0, ZoneOffset.ofHours(8));

    @Autowired
    private FinanceReceiptService financeReceiptService;

    @Autowired
    private FinancePaymentService financePaymentService;

    @Autowired
    private FinanceWriteOffService financeWriteOffService;

    @Autowired
    private FinanceReceivableQueryService receivableQueryService;

    @Autowired
    private FinancePayableQueryService payableQueryService;

    // ------------------------------------------------------------------
    // 应收
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应收全额红冲：净额归零即已结清，OPEN 筛选不再命中")
    void fullyRedFlushedReceivableIsSettled() {
        Long customerId = newCustomer();
        Long receivableId = normalReceivable(customerId, "100.0000");
        insertRedReceivable(receivableId, customerId, "100.0000");

        var header = receivableQueryService.detail(receivableId).getReceivable();

        assertThat(header.getNetAmount()).isEqualByComparingTo("0.0000");
        assertThat(header.getWrittenOffAmount()).isEqualByComparingTo("0.0000");
        assertThat(header.getOpenAmount()).isEqualByComparingTo("0.0000");
        assertThat(header.getOverAppliedAmount()).isEqualByComparingTo("0.0000");
        assertThat(header.getSettleState()).isEqualTo("SETTLED");
        assertThat(receivableIdsFilteredBy("SETTLED")).contains(receivableId);
        assertThat(receivableIdsFilteredBy("OPEN")).doesNotContain(receivableId);
    }

    @Test
    @DisplayName("应收部分红冲且无核销：仍有净额待收，保持未结清")
    void partiallyRedFlushedReceivableStaysOpen() {
        Long customerId = newCustomer();
        Long receivableId = normalReceivable(customerId, "100.0000");
        insertRedReceivable(receivableId, customerId, "40.0000");

        var header = receivableQueryService.detail(receivableId).getReceivable();

        assertThat(header.getNetAmount()).isEqualByComparingTo("60.0000");
        assertThat(header.getOpenAmount()).isEqualByComparingTo("60.0000");
        assertThat(header.getSettleState()).isEqualTo("OPEN");
        assertThat(receivableIdsFilteredBy("OPEN")).contains(receivableId);
        assertThat(receivableIdsFilteredBy("SETTLED")).doesNotContain(receivableId);
    }

    @Test
    @DisplayName("应收部分核销为 PARTIAL，核销额恰等于净额为 SETTLED")
    void receivablePartialAndExactWriteOff() {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = addReceipt(customerId, "200.0000");
        Long partialId = normalReceivable(customerId, "100.0000");
        Long exactId = normalReceivable(customerId, "70.0000");
        financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(partialId, "30.0000")),
                key("partial-write-off"));
        financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(exactId, "70.0000")),
                key("exact-write-off"));

        var partial = receivableQueryService.detail(partialId).getReceivable();
        assertThat(partial.getWrittenOffAmount()).isEqualByComparingTo("30.0000");
        assertThat(partial.getOpenAmount()).isEqualByComparingTo("70.0000");
        assertThat(partial.getSettleState()).isEqualTo("PARTIAL");

        var exact = receivableQueryService.detail(exactId).getReceivable();
        assertThat(exact.getOpenAmount()).isEqualByComparingTo("0.0000");
        assertThat(exact.getSettleState()).isEqualTo("SETTLED");

        assertThat(receivableIdsFilteredBy("PARTIAL")).contains(partialId).doesNotContain(exactId);
        assertThat(receivableIdsFilteredBy("SETTLED")).contains(exactId).doesNotContain(partialId);
    }

    @Test
    @DisplayName("应收先核销后红冲造成超额核销：保持 PARTIAL，不得误标记已结清")
    void overAppliedReceivableStaysPartial() {
        Long customerId = newCustomer();
        FinanceReceiptVO receipt = addReceipt(customerId, "90.0000");
        Long receivableId = normalReceivable(customerId, "100.0000");
        financeWriteOffService.add(
                writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(receivableId, "90.0000")),
                key("write-off-before-red"));
        insertRedReceivable(receivableId, customerId, "30.0000");

        var header = receivableQueryService.detail(receivableId).getReceivable();

        assertThat(header.getNetAmount()).isEqualByComparingTo("70.0000");
        assertThat(header.getWrittenOffAmount()).isEqualByComparingTo("90.0000");
        assertThat(header.getOpenAmount()).isEqualByComparingTo("0.0000");
        assertThat(header.getOverAppliedAmount()).isEqualByComparingTo("20.0000");
        assertThat(header.getSettleState()).isEqualTo("PARTIAL");
        assertThat(receivableIdsFilteredBy("SETTLED")).doesNotContain(receivableId);
        assertThat(receivableIdsFilteredBy("PARTIAL")).contains(receivableId);
    }

    // ------------------------------------------------------------------
    // 应付
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应付全额红冲：净额归零即已结清，OPEN 筛选不再命中")
    void fullyRedFlushedPayableIsSettled() {
        Long supplierId = newSupplier("SS-FULL-RED");
        Long payableId = normalPayable(supplierId, "100.0000");
        insertRedPayable(payableId, supplierId, "100.0000");

        var header = payableQueryService.detail(payableId).getPayable();

        assertThat(header.getNetAmount()).isEqualByComparingTo("0.0000");
        assertThat(header.getOpenAmount()).isEqualByComparingTo("0.0000");
        assertThat(header.getSettleState()).isEqualTo("SETTLED");
        assertThat(payableIdsFilteredBy("SETTLED")).contains(payableId);
        assertThat(payableIdsFilteredBy("OPEN")).doesNotContain(payableId);
    }

    @Test
    @DisplayName("应付部分核销为 PARTIAL，核销额恰等于净额为 SETTLED")
    void payablePartialAndExactWriteOff() {
        Long supplierId = newSupplier("SS-WO");
        FinancePaymentVO payment = addSupplierPayment(supplierId, "200.0000");
        Long partialId = normalPayable(supplierId, "100.0000");
        Long exactId = normalPayable(supplierId, "70.0000");
        financeWriteOffService.add(
                writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(partialId, "30.0000")),
                key("payable-partial-write-off"));
        financeWriteOffService.add(
                writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(exactId, "70.0000")),
                key("payable-exact-write-off"));

        var partial = payableQueryService.detail(partialId).getPayable();
        assertThat(partial.getWrittenOffAmount()).isEqualByComparingTo("30.0000");
        assertThat(partial.getOpenAmount()).isEqualByComparingTo("70.0000");
        assertThat(partial.getSettleState()).isEqualTo("PARTIAL");

        var exact = payableQueryService.detail(exactId).getPayable();
        assertThat(exact.getOpenAmount()).isEqualByComparingTo("0.0000");
        assertThat(exact.getSettleState()).isEqualTo("SETTLED");

        assertThat(payableIdsFilteredBy("PARTIAL")).contains(partialId).doesNotContain(exactId);
        assertThat(payableIdsFilteredBy("SETTLED")).contains(exactId).doesNotContain(partialId);
    }

    @Test
    @DisplayName("应付先核销后红冲造成超额核销：保持 PARTIAL，不得误标记已结清")
    void overAppliedPayableStaysPartial() {
        Long supplierId = newSupplier("SS-OVER");
        FinancePaymentVO payment = addSupplierPayment(supplierId, "90.0000");
        Long payableId = normalPayable(supplierId, "100.0000");
        financeWriteOffService.add(
                writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(payableId, "90.0000")),
                key("payable-write-off-before-red"));
        insertRedPayable(payableId, supplierId, "30.0000");

        var header = payableQueryService.detail(payableId).getPayable();

        assertThat(header.getNetAmount()).isEqualByComparingTo("70.0000");
        assertThat(header.getWrittenOffAmount()).isEqualByComparingTo("90.0000");
        assertThat(header.getOpenAmount()).isEqualByComparingTo("0.0000");
        assertThat(header.getOverAppliedAmount()).isEqualByComparingTo("20.0000");
        assertThat(header.getSettleState()).isEqualTo("PARTIAL");
        assertThat(payableIdsFilteredBy("SETTLED")).doesNotContain(payableId);
        assertThat(payableIdsFilteredBy("PARTIAL")).contains(payableId);
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /**
     * 直接插一条正常应收。汇总 SQL 与 {@code sales_order} 是 INNER JOIN，
     * 因此订单必须真实存在（走订单命令链造单，不用不存在的 id）。
     */
    private Long normalReceivable(Long customerId, String amount) {
        // SKU 编码含用例前缀但不含序号：同一用例造两条应收时必须各占一个后缀，否则撞唯一索引
        Long orderId = confirmedSalesOrder(customerId,
                newOnShelfSku("SS-AR-" + UUID.randomUUID().toString().substring(0, 6)), "10.0000", "10.0000");
        return jdbc.queryForObject("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id,"
                        + " customer_id, customer_name_snapshot, settlement_customer_id,"
                        + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                        + " VALUES (?, 'SALES_ORDER', ?, ?, ?, '结清测试客户', ?, '结清测试客户', 'NORMAL', ?,"
                        + " now(), now(), now()) RETURNING id",
                Long.class, "AR-SS-" + prefix + "-" + UUID.randomUUID(), uniqueNumber(), orderId, customerId,
                customerId, new BigDecimal(amount));
    }

    private void insertRedReceivable(Long originalId, Long customerId, String amount) {
        Long orderId = jdbc.queryForObject("SELECT order_id FROM finance_receivable WHERE id = ?",
                Long.class, originalId);
        jdbc.update("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id, customer_id,"
                        + " customer_name_snapshot, settlement_customer_id,"
                        + " settlement_customer_name_snapshot, entry_type, original_receivable_id, amount, event_at,"
                        + " reason)"
                        + " VALUES (?, 'ORDER_RETURN', ?, ?, ?, '结清测试客户', ?, '结清测试客户', 'RED', ?, ?, now(),"
                        + " '测试红字')",
                "AR-SS-RED-" + prefix + "-" + UUID.randomUUID(), uniqueNumber(), orderId, customerId, customerId,
                originalId, new BigDecimal(amount));
    }

    private Long normalPayable(Long supplierId, String amount) {
        Long payableId = jdbc.queryForObject("INSERT INTO finance_payable (payable_no, source_type, source_id,"
                        + " purchase_order_id, supplier_id, supplier_name_snapshot, entry_type, amount, event_at)"
                        + " VALUES (?, 'PURCHASE_RECEIPT', ?, 1, ?, '结清测试供应商', 'NORMAL', ?, now()) RETURNING id",
                Long.class, "AP-SS-" + prefix + "-" + UUID.randomUUID(), uniqueNumber(), supplierId,
                new BigDecimal(amount));
        jdbc.update("INSERT INTO finance_payable_item (payable_id, source_type, source_id, purchase_order_item_id,"
                        + " sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount)"
                        + " VALUES (?, 'PURCHASE_RECEIPT_ITEM', ?, 44, 1, '测试商品', 'kg', 10.0000, 10.0000, 100.0000)",
                payableId, uniqueNumber());
        return payableId;
    }

    /** 红字应付的库级配对约束：MANUAL 来源、source_id 为空、必须引用原单并写原因。 */
    private void insertRedPayable(Long originalId, Long supplierId, String amount) {
        jdbc.update("INSERT INTO finance_payable (payable_no, source_type, source_id, purchase_order_id,"
                        + " supplier_id, supplier_name_snapshot, entry_type, original_payable_id, amount, event_at,"
                        + " reason)"
                        + " VALUES (?, 'MANUAL', NULL, 1, ?, '结清测试供应商', 'RED', ?, ?, now(), '测试红字')",
                "AP-SS-RED-" + prefix + "-" + UUID.randomUUID(), supplierId, originalId, new BigDecimal(amount));
    }

    private FinanceReceiptVO addReceipt(Long customerId, String amount) {
        var form = new com.xsy.scm.finance.domain.form.FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setReceivedAt(EVENT_AT);
        return financeReceiptService.add(form, key("receipt-add"));
    }

    private FinancePaymentVO addSupplierPayment(Long supplierId, String amount) {
        var form = new com.xsy.scm.finance.domain.form.FinancePaymentAddForm();
        form.setCounterpartyType("SUPPLIER");
        form.setCounterpartyId(supplierId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setPaidAt(EVENT_AT);
        return financePaymentService.add(form, key("payment-add"));
    }

    private List<Long> receivableIdsFilteredBy(String settleState) {
        var form = new com.xsy.scm.finance.domain.form.FinanceReceivableQueryForm();
        form.setPageNum(1L);
        form.setPageSize(100L);
        form.setSettleState(settleState);
        form.setStartDate(LocalDate.now(ZoneOffset.ofHours(8)).minusDays(1));
        form.setEndDate(LocalDate.now(ZoneOffset.ofHours(8)));
        return receivableQueryService.query(form).getList().stream()
                .map(com.xsy.scm.finance.domain.vo.FinanceReceivableVO::getReceivableId).toList();
    }

    private List<Long> payableIdsFilteredBy(String settleState) {
        var form = new com.xsy.scm.finance.domain.form.FinancePayableQueryForm();
        form.setPageNum(1L);
        form.setPageSize(100L);
        form.setSettleState(settleState);
        form.setStartDate(LocalDate.now(ZoneOffset.ofHours(8)).minusDays(1));
        form.setEndDate(LocalDate.now(ZoneOffset.ofHours(8)));
        return payableQueryService.query(form).getList().stream()
                .map(com.xsy.scm.finance.domain.vo.FinancePayableVO::getPayableId).toList();
    }

    private FinanceWriteOffAddForm writeOffForm(String sourceType, Long sourceId,
            FinanceWriteOffAddItemForm... items) {
        FinanceWriteOffAddForm form = new FinanceWriteOffAddForm();
        form.setSourceType(sourceType);
        form.setSourceId(sourceId);
        form.setItems(List.of(items));
        return form;
    }

    private FinanceWriteOffAddItemForm writeOffItem(Long targetId, String amount) {
        FinanceWriteOffAddItemForm item = new FinanceWriteOffAddItemForm();
        item.setTargetId(targetId);
        item.setAmount(amount);
        return item;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private long uniqueNumber() {
        return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
    }
}
