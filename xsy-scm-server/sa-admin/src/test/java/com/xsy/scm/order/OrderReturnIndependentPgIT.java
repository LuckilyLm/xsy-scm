package com.xsy.scm.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAutoForm;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import com.xsy.scm.order.domain.form.OrderReturnIndependentAddForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentItemForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveItemForm;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.domain.vo.OrderReturnReceiptVO;
import com.xsy.scm.order.service.OrderReturnReceiptService;
import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.report.constant.ScmFinanceAgingAccountTypeEnum;
import com.xsy.scm.report.domain.form.ScmCustomerStatementForm;
import com.xsy.scm.report.domain.form.ScmFinanceAgingQueryForm;
import com.xsy.scm.report.service.ScmCustomerStatementService;
import com.xsy.scm.report.service.ScmFinanceAgingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 独立退货单（不关联原订单，D-1028-15）。
 *
 * <p>覆盖三件事：建单取「最近成交价」并生成客户级红字；实物接收按仓库均价回库、不绑原出库行； 客户级红字在账龄、核销与对账单中按客户先进先出抵减最早未结应收。
 */
class OrderReturnIndependentPgIT extends ScmW5PgITBase {

    private static final OffsetDateTime EVENT_AT = OffsetDateTime.of(2026, 3, 17, 9, 0, 0, 0,
        ZoneOffset.ofHours(8));

    @Autowired
    private OrderReturnService orderReturnService;

    @Autowired
    private OrderReturnReceiptService orderReturnReceiptService;

    @Autowired
    private FinanceReceiptService financeReceiptService;

    @Autowired
    private FinanceWriteOffService financeWriteOffService;

    @Autowired
    private ScmFinanceAgingService financeAgingService;

    @Autowired
    private ScmCustomerStatementService customerStatementService;

    @Test
    @DisplayName("#1 独立退货按最近成交价锁价，审批生成客户级红字且不产生退款单")
    void independentReturnLocksRecentTransactionPriceAndGeneratesCustomerLevelRed() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("IR1");
        confirmedSalesOrder(customerId, skuId, "10", "10");

        OrderReturnDetailVO created = orderReturnService.createIndependent(addForm(customerId, skuId, "3"), key("ir"));
        assertThat(created.getOrderId()).isNull();
        assertThat(created.getSourceType()).isEqualTo("INDEPENDENT");
        assertThat(created.getCustomerName()).isNotBlank();
        var item = created.getItems().getFirst();
        assertThat(item.getLockedUnitPrice()).isEqualByComparingTo("1.2000");
        assertThat(item.getPriceSource()).isEqualTo("RECENT_TRANSACTION");

        OrderReturnDetailVO approved = orderReturnService.approveIndependent(approveForm(created), key("ir-approve"));
        assertThat(approved.getApprovedAmount()).isEqualByComparingTo("3.6000");

        assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
            + " AND source_id = ? AND entry_type = 'RED'", created.getReturnId())).isEqualTo(1);
        assertThat(query("SELECT order_id FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
            + " AND source_id = ?", created.getReturnId())).isNull();
        assertThat(query("SELECT original_receivable_id FROM xsy_finance_receivable WHERE source_type ="
            + " 'ORDER_RETURN' AND source_id = ?", created.getReturnId())).isNull();
        assertThat(decimal("SELECT amount FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
            + " AND source_id = ?", created.getReturnId())).isEqualByComparingTo("3.6000");
        assertThat(count("SELECT count(*) FROM xsy_order_refund WHERE return_id = ?", created.getReturnId())).isZero();
    }

    @Test
    @DisplayName("#2 独立退货回库不绑原出库行，成本取入库前该仓移动加权均价")
    void independentReturnReceiptPostsStockAtWarehouseAverageCost() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("IR2");
        Long warehouseId = newWarehouse("IR");
        confirmedSalesOrder(customerId, skuId, "10", "10");
        OrderReturnDetailVO approved = approve(orderReturnService
            .createIndependent(addForm(customerId, skuId, "2"), key("ir-receive")), key("ir-receive-approve"));
        jdbc.update("INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, avg_cost)"
            + " VALUES (?, ?, 'kg', 10, 5)", warehouseId, skuId);

        OrderReturnReceiptVO receipt = orderReturnReceiptService.receive(receiveForm(approved, warehouseId),
            key("ir-receipt"));

        var line = receipt.getItems().getFirst();
        assertThat(line.getSourceSalesOutMovementId()).isNull();
        assertThat(line.getSourceOutboundItemId()).isNull();
        assertThat(line.getStockQuantity()).isEqualByComparingTo("2.0000");
        // 成本列对无成本权限的账号会被脱敏，入库成本改从库里核对。
        assertThat(decimal("SELECT unit_cost FROM xsy_order_return_receipt_item WHERE id = ?",
            line.getReceiptItemId())).isEqualByComparingTo("5.0000");
        assertThat(decimal("SELECT quantity FROM xsy_inventory_balance WHERE warehouse_id = ? AND sku_id = ?",
            warehouseId, skuId)).isEqualByComparingTo("12.0000");
        assertThat(decimal("SELECT avg_cost FROM xsy_inventory_balance WHERE warehouse_id = ? AND sku_id = ?",
            warehouseId, skuId)).isEqualByComparingTo("5.0000");
        assertThat(count("SELECT count(*) FROM xsy_inventory_movement WHERE movement_type = 'SALES_RETURN_IN'"
            + " AND source_document_id = ?", approved.getReturnId())).isEqualTo(1);
    }

    @Test
    @DisplayName("#3 客户级红字按客户先进先出抵减最早未结应收：账龄、核销、对账单口径一致")
    void customerLevelRedReducesAgingAndWriteOffFifo() {
        Long customerId = newCustomer();
        Long oldestOrder = confirmedSalesOrder(customerId, newOnShelfSku("IR3A"), "10", "10");
        Long newestOrder = confirmedSalesOrder(customerId, newOnShelfSku("IR3B"), "10", "10");
        Long oldest = normalReceivable(customerId, oldestOrder, "100.0000", EVENT_AT.minusDays(10));
        Long newest = normalReceivable(customerId, newestOrder, "50.0000", EVENT_AT.minusDays(5));
        customerLevelRed(customerId, "30.0000", EVENT_AT.minusDays(3));

        ScmFinanceAgingQueryForm agingForm = new ScmFinanceAgingQueryForm();
        agingForm.setAccountType(ScmFinanceAgingAccountTypeEnum.RECEIVABLE);
        agingForm.setAsOfDate(LocalDate.of(2026, 3, 18));
        agingForm.setCustomerId(customerId);
        agingForm.setPageNum(1L);
        agingForm.setPageSize(50L);
        // 账龄按「到期日升序、单据 ID 降序」展示：两单都没有到期日，因此新单在前。
        var rows = financeAgingService.query(agingForm).getList();
        assertThat(rows).extracting(row -> row.getDocumentId()).containsExactly(newest, oldest);
        assertThat(rows).extracting(row -> row.getOpenAmount())
            .containsExactly(new BigDecimal("50.0000"), new BigDecimal("70.0000"));
        assertThat(financeAgingService.summary(agingForm)).extracting(row -> row.getOpenAmount())
            .containsExactly(new BigDecimal("120.0000"));

        FinanceReceiptVO receipt = addReceipt(customerId, "200.0000");
        FinanceWriteOffAutoForm autoForm = new FinanceWriteOffAutoForm();
        autoForm.setSourceType("RECEIPT");
        autoForm.setSourceId(receipt.getReceiptId());
        var plan = financeWriteOffService.autoPreview(autoForm);
        assertThat(plan).extracting(row -> row.getAllocatedAmount())
            .containsExactly(new BigDecimal("70.0000"), new BigDecimal("50.0000"));

        ScmCustomerStatementForm statementForm = new ScmCustomerStatementForm();
        statementForm.setSettlementCustomerId(customerId);
        statementForm.setCustomerId(customerId);
        statementForm.setStartDate(LocalDate.of(2026, 3, 1));
        statementForm.setEndDate(LocalDate.of(2026, 3, 18));
        var statement = customerStatementService.freeze(statementForm);
        assertThat(statement.getItems()).filteredOn(line -> "RED".equals(line.getFactType()))
            .extracting(line -> line.getReceivableDelta()).containsExactly(new BigDecimal("-30.0000"));
    }

    private OrderReturnIndependentAddForm addForm(Long customerId, Long skuId, String quantity) {
        OrderReturnIndependentAddForm form = new OrderReturnIndependentAddForm();
        form.setCustomerId(customerId);
        form.setReason("现场退回，找不到原单");
        OrderReturnIndependentItemForm item = new OrderReturnIndependentItemForm();
        item.setSkuId(skuId);
        item.setRequestedQuantity(quantity);
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private OrderReturnIndependentApproveForm approveForm(OrderReturnDetailVO created) {
        OrderReturnIndependentApproveForm form = new OrderReturnIndependentApproveForm();
        form.setReturnId(created.getReturnId());
        form.setVersion(created.getVersion());
        OrderReturnIndependentApproveForm.Item item = new OrderReturnIndependentApproveForm.Item();
        item.setReturnItemId(created.getItems().getFirst().getReturnItemId());
        item.setApprovedQuantity(created.getItems().getFirst().getRequestedQuantity().toPlainString());
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private OrderReturnDetailVO approve(OrderReturnDetailVO created, String key) {
        return orderReturnService.approveIndependent(approveForm(created), key);
    }

    private OrderReturnReceiveForm receiveForm(OrderReturnDetailVO approved, Long warehouseId) {
        OrderReturnReceiveForm form = new OrderReturnReceiveForm();
        form.setReturnId(approved.getReturnId());
        form.setWarehouseId(warehouseId);
        form.setVersion(approved.getVersion());
        OrderReturnReceiveItemForm item = new OrderReturnReceiveItemForm();
        item.setReturnItemId(approved.getItems().getFirst().getReturnItemId());
        item.setDisposition("RETURN_TO_STOCK");
        item.setQuantity(approved.getItems().getFirst().getApprovedQuantity().toPlainString());
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private FinanceReceiptVO addReceipt(Long customerId, String amount) {
        FinanceReceiptAddForm form = new FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setReceivedAt(EVENT_AT);
        return financeReceiptService.add(form, key("receipt"));
    }

    private Long normalReceivable(Long customerId, Long salesOrderId, String amount, OffsetDateTime eventAt) {
        return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id,"
                + " order_id, customer_id, customer_name_snapshot, settlement_customer_id,"
                + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                + " VALUES (?, 'SALES_ORDER', ?, ?, ?, '独立退货测试客户', ?, '独立退货测试客户', 'NORMAL', ?, ?,"
                + " now(), now()) RETURNING id",
            Long.class, "AR-IR-" + UUID.randomUUID(), salesOrderId, salesOrderId, customerId, customerId,
            new BigDecimal(amount), eventAt);
    }

    private Long customerLevelRed(Long customerId, String amount, OffsetDateTime eventAt) {
        return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id,"
                + " order_id, customer_id, customer_name_snapshot, settlement_customer_id,"
                + " settlement_customer_name_snapshot, entry_type, amount, event_at, reason, created_at, updated_at)"
                + " VALUES (?, 'ORDER_RETURN', ?, NULL, ?, '独立退货测试客户', ?, '独立退货测试客户', 'RED', ?, ?,"
                + " '独立退货', now(), now()) RETURNING id",
            Long.class, "AR-IR-RED-" + UUID.randomUUID(),
            1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L), customerId,
            customerId, new BigDecimal(amount), eventAt);
    }

    private Object query(String sql, Object... args) {
        return jdbc.queryForObject(sql, Object.class, args);
    }

    private BigDecimal decimal(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }
}
