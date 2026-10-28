package com.xsy.scm.order;

import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.order.service.*;
import com.xsy.scm.order.domain.form.*;
import com.xsy.scm.order.domain.vo.*;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.domain.form.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class SalesOrderServiceIT extends ScmW3PgITBase {
    @Autowired
    SalesOrderService orders;
    @Autowired
    SalesOrderQueryService query;
    @Autowired
    OrderReturnService returns;
    @Autowired
    OrderRefundService refunds;
    @Autowired
    CustomerService customers;
    @Autowired
    SalesOrderImportService orderImports;
    @Autowired
    org.apache.ibatis.session.SqlSession session;

    Long customer() {
        var f = new CustomerAddForm();
        f.setName(prefix);
        f.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        f.setSettleMode("INDEPENDENT");
        f.setSellerId(anyEmployeeId());
        // 送货单导入按客户档案取收货人、电话与地址；其余用例不受影响。
        f.setContactName("收货人");
        f.setContactPhone("13800000000");
        f.setAddress("测试地址");
        Long id = customers.add(f);
        var s = new CustomerStatusForm();
        s.setCustomerId(id);
        s.setVersion(0);
        s.setStatus("COOPERATING");
        customers.updateStatus(s);
        return id;
    }

    SalesOrderAddForm form(Long c, Long sku) {
        var f = new SalesOrderAddForm();
        f.setCustomerId(c);
        f.setOrderSource("ADMIN");
        f.setRemark("测试订单");
        var a = new OrderAddressForm();
        a.setReceiverName("测试客户");
        a.setReceiverPhone("13800000000");
        a.setAddress("测试地址");
        f.setAddress(a);
        var i = new SalesOrderItemForm();
        i.setSkuId(sku);
        i.setOrderedQuantity("2.0000");
        i.setManualPriceOverride(false);
        f.setItems(new ArrayList<>(List.of(i)));
        return f;
    }

    OrderVersionForm version(SalesOrderDetailVO o) {
        var f = new OrderVersionForm();
        f.setOrderId(o.getOrderId());
        f.setVersion(o.getVersion());
        return f;
    }

    SalesOrderDetailVO confirmed(Long c, Long sku) {
        // 键按次唯一：真提交的用例重跑时不能撞上上一次留下的幂等记录。
        var o = orders.create(form(c, sku), prefix + "c-" + UUID.randomUUID());
        o = orders.submit(version(o), prefix + "s");
        var a = new OrderActualQuantityForm();
        a.setOrderId(o.getOrderId());
        a.setItemId(o.getItems().getFirst().getItemId());
        a.setVersion(o.getItems().getFirst().getVersion());
        a.setActualQuantity("1.5000");
        a.setReason("实际称重");
        o = orders.actualQuantity(a, prefix + "a");
        return orders.confirm(version(o), prefix + "f");
    }

    @Test
    void lifecycleRetainsSnapshotsAndLockedPricesAndAudit() {
        var c = customer();
        var sku = newOnShelfSku("L");
        var f = form(c, sku);
        var o = orders.create(f, prefix);
        assertThat(o.getVersion()).isZero();
        assertThat(o.getSettleModeSnapshot()).isEqualTo("INDEPENDENT");
        assertThat(o.getSellerId()).isEqualTo(anyEmployeeId());
        var u = new SalesOrderUpdateForm();
        org.springframework.beans.BeanUtils.copyProperties(f, u);
        u.setOrderId(o.getOrderId());
        u.setVersion(o.getVersion());
        u.getItems().getFirst().setItemId(o.getItems().getFirst().getItemId());
        u.getItems().getFirst().setVersion(o.getItems().getFirst().getVersion());
        u.getItems().getFirst().setOrderedQuantity("3.0000");
        var updated = orders.update(u);
        assertThat(updated.getItems().getFirst().getItemId()).isEqualTo(o.getItems().getFirst().getItemId());
        expectCode(() -> orders.update(u), 40921);
        jdbc.update("UPDATE xsy_product_sku SET market_price=2.5000 WHERE id=?", sku);
        session.clearCache();
        // 行情变了以后提交会被「报价依据已变化」拦下，必须显式核价留痕——这正是设计口径。
        var stale = updated;
        expectCode(() -> orders.submit(version(stale), prefix + "stale"), 40975);
        var reprice = new OrderRepriceForm();
        reprice.setOrderId(updated.getOrderId());
        reprice.setVersion(updated.getVersion());
        reprice.setReason("行情涨价");
        updated = orders.reprice(reprice);
        o = orders.submit(version(updated), prefix + "s");
        assertThat(o.getOrderedTotalAmount()).isEqualByComparingTo("7.5000");
        assertThat(o.getItems().getFirst().getActualQuantity()).isNull();
        jdbc.update("UPDATE xsy_product_sku SET market_price=8.0000 WHERE id=?", sku);
        jdbc.update("UPDATE xsy_customer SET name='changed',settle_mode='GROUP',seller_id=NULL WHERE id=?", c);
        session.clearCache();
        // 非标未称重也能确认：不要求销售先填估计实重，金额按订购量暂计，称重后回写实际值
        o = orders.confirm(version(o), prefix + "confirm");
        assertThat(o.getStatus()).isEqualTo("CONFIRMED");
        assertThat(o.getItems().getFirst().getActualQuantity()).isNull();
        assertThat(o.getSettlementTotalAmount()).isEqualByComparingTo("7.5000");
        var a = new OrderActualQuantityForm();
        a.setOrderId(o.getOrderId());
        a.setItemId(o.getItems().getFirst().getItemId());
        a.setVersion(o.getItems().getFirst().getVersion());
        a.setActualQuantity("2.1234");
        a.setReason("人工称重");
        o = orders.actualQuantity(a, prefix + "actual");
        assertThat(o.getSettlementTotalAmount()).isEqualByComparingTo("5.3085");
        assertThat(o.getOrderedTotalAmount()).isEqualByComparingTo("7.5000");
        assertThat(o.getSettleModeSnapshot()).isEqualTo("INDEPENDENT");
        assertThat(o.getCustomerNameSnapshot()).isEqualTo(prefix);
        assertThat(o.getItems().getFirst().getLockedUnitPrice()).isEqualByComparingTo("2.5000");
        var logs = new OrderLogQueryForm();
        logs.setOrderId(o.getOrderId());
        logs.setPageNum(1L);
        logs.setPageSize(20L);
        assertThat(query.logs(logs).getList()).extracting(OrderOperationLogVO::getOperationType).contains("CREATE", "UPDATE", "SUBMIT", "ACTUAL_QUANTITY", "CONFIRM");
        assertThat(query.logs(logs).getList()).allSatisfy(l -> {
            assertThat(l.getOperator()).isEqualTo("1:1");
            assertThat(l.getAfterData()).isNotEmpty();
        });
        var jsonValue = objectMapper.valueToTree(o);
        assertThat(jsonValue.get("settlementTotalAmount").asText()).isEqualTo("5.3085");
    }

    @Test
    void createAndProgressConfirmsStandardButKeepsNonStandardPending() {
        var customer = customer();
        var standardSku = newOnShelfSku("AUTO_STANDARD");
        jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", standardSku);
        session.clearCache();
        var standard = orders.createAndProgress(form(customer, standardSku), prefix + "standard");
        assertThat(standard.getStatus()).isEqualTo("CONFIRMED");
        assertThat(standard.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("2.0000");
        assertThat(orders.createAndProgress(form(customer, standardSku), prefix + "standard").getOrderId()).isEqualTo(standard.getOrderId());

        var nonStandard = orders.createAndProgress(form(customer, newOnShelfSku("AUTO_WEIGHT")), prefix + "weight");
        assertThat(nonStandard.getStatus()).isEqualTo("PENDING");
        assertThat(nonStandard.getItems().getFirst().getActualQuantity()).isNull();
    }

    @Test
    void pendingOrderEditRelocksQuantityAndKeepsTheSubmittedPrice() {
        var c = customer();
        var sku = newOnShelfSku("PENDING_EDIT");
        jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", sku);
        session.clearCache();
        var pending = orders.submit(version(orders.create(form(c, sku), prefix + "pe")), prefix + "pe-submit");
        assertThat(pending.getStatus()).isEqualTo("PENDING");
        var quotedUnitPrice = pending.getItems().getFirst().getLockedUnitPrice();

        var u = new SalesOrderUpdateForm();
        org.springframework.beans.BeanUtils.copyProperties(form(c, sku), u);
        u.setOrderId(pending.getOrderId());
        u.setVersion(pending.getVersion());
        u.getItems().getFirst().setItemId(pending.getItems().getFirst().getItemId());
        u.getItems().getFirst().setVersion(pending.getItems().getFirst().getVersion());
        u.getItems().getFirst().setOrderedQuantity("3.0000");
        var edited = orders.update(u);

        assertThat(edited.getStatus()).isEqualTo("PENDING");
        assertThat(edited.getItems().getFirst().getOrderedQuantity()).isEqualByComparingTo("3.0000");
        // 改单沿用提交时的报价，不静默改价；需要新价时走批量核价。
        assertThat(edited.getItems().getFirst().getLockedUnitPrice()).isEqualByComparingTo(quotedUnitPrice);
        assertThat(edited.getOrderedTotalAmount()).isEqualByComparingTo("3.6000");
        // 标品实数量必须跟着下单量走，否则确认前会被实数量校验拦下。
        assertThat(edited.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("3.0000");
        assertThat(logCount(pending.getOrderId(), "UPDATE")).isEqualTo(1);
    }

    @Test
    void pendingOrderWithPaymentFactRefusesFurtherEdits() {
        var c = customer();
        var sku = newOnShelfSku("PENDING_BLOCKED");
        var pending = orders.submit(version(orders.create(form(c, sku), prefix + "pb")), prefix + "pb-submit");
        jdbc.queryForObject("INSERT INTO xsy_payment_intent (intent_no, customer_id, customer_name_snapshot,"
                + " source_type, source_id, source_no_snapshot, amount, method, provider, status,"
                + " created_by, updated_by) VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, 1.0000, 'ONLINE', 'MOCK',"
                + " 'PENDING', '1:1', '1:1') RETURNING id",
                Long.class, "PI-" + prefix + "-" + UUID.randomUUID(), c, prefix, pending.getOrderId(),
                "SO-" + pending.getOrderId());
        session.clearCache();
        var u = new SalesOrderUpdateForm();
        org.springframework.beans.BeanUtils.copyProperties(form(c, sku), u);
        u.setOrderId(pending.getOrderId());
        u.setVersion(pending.getVersion());
        u.getItems().getFirst().setItemId(pending.getItems().getFirst().getItemId());
        u.getItems().getFirst().setVersion(pending.getItems().getFirst().getVersion());
        u.getItems().getFirst().setOrderedQuantity("5.0000");
        expectCode(() -> orders.update(u), 41204);
        assertThat(jdbc.queryForObject("SELECT ordered_quantity FROM xsy_sales_order_item WHERE id=?",
                java.math.BigDecimal.class, pending.getItems().getFirst().getItemId()))
                .isEqualByComparingTo("2.0000");
    }

    @Test
    void confirmedOrderAcceptsActualQuantityUntilDispatch() {
        var c = customer();
        var sku = newOnShelfSku("ACTUAL_CONFIRMED");
        var order = confirmed(c, sku);
        assertThat(order.getStatus()).isEqualTo("CONFIRMED");
        assertThat(order.getSettlementTotalAmount()).isEqualByComparingTo("1.8000");

        var a = new OrderActualQuantityForm();
        a.setOrderId(order.getOrderId());
        a.setItemId(order.getItems().getFirst().getItemId());
        a.setVersion(order.getItems().getFirst().getVersion());
        a.setActualQuantity("2.4000");
        a.setReason("复称");
        var edited = orders.actualQuantity(a, prefix + "ac-" + UUID.randomUUID());

        assertThat(edited.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("2.4000");
        // 已确认订单的实发量就是结算量，订单结算合计必须同步。
        assertThat(edited.getSettlementTotalAmount()).isEqualByComparingTo("2.8800");
        assertThat(logCount(order.getOrderId(), "ACTUAL_QUANTITY")).isEqualTo(2);

        // 发车出库后不再允许改实发量，否则与已出库的分拣记录脱节。
        jdbc.update("UPDATE xsy_sales_order SET dispatched_at=CURRENT_TIMESTAMP WHERE id=?", order.getOrderId());
        session.clearCache();
        var stale = new OrderActualQuantityForm();
        stale.setOrderId(edited.getOrderId());
        stale.setItemId(edited.getItems().getFirst().getItemId());
        stale.setVersion(edited.getItems().getFirst().getVersion());
        stale.setActualQuantity("3.0000");
        stale.setReason("复称");
        expectCode(() -> orders.actualQuantity(stale, prefix + "ac2-" + UUID.randomUUID()), 40917);
    }

    @Test
    void excelImportIsAtomicAndKeepsNonStandardPending() throws Exception {
        customer();
        var standardSku = newOnShelfSku("IMPORT_STANDARD");
        jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", standardSku);
        var nonStandardSku = newOnShelfSku("IMPORT_WEIGHT");
        session.clearCache();
        var standard = productOf(standardSku);
        var weight = productOf(nonStandardSku);

        // 一张送货单生成一张订单：同时含标品与称重品时整单待称重。
        var mixedFile = deliveryNote(prefix, List.of(noteLine(standard, "2.0000"), noteLine(weight, "3.0000")));
        var mixed = orderImports.importFile(mixedFile, prefix + "import", true);
        assertThat(mixed.getTotalErrors()).isZero();
        assertThat(mixed.getTotalOrders()).isOne();
        assertThat(mixed.getPendingOrders()).isOne();
        assertThat(mixed.getOrders()).singleElement().satisfies(o -> {
            assertThat(o.getOrderSource()).isEqualTo("IMPORT");
            assertThat(o.getStatus()).isEqualTo("PENDING");
            assertThat(o.getItems()).filteredOn(item -> item.getSkuId().equals(standardSku)).singleElement()
                    .satisfies(item -> assertThat(item.getActualQuantity()).isEqualByComparingTo("2.0000"));
        });
        // 同一文件 + 同一幂等键重放，不再产生第二张订单。
        assertThat(orderImports.importFile(mixedFile, prefix + "import", true).getOrders())
                .extracting(SalesOrderDetailVO::getOrderId)
                .containsExactlyElementsOf(mixed.getOrders().stream().map(SalesOrderDetailVO::getOrderId).toList());

        // 只含标品的送货单直接确认。
        var single = orderImports.importFile(
                deliveryNote(prefix, List.<String[]>of(noteLine(standard, "1.0000"))), prefix + "single", true);
        assertThat(single.getConfirmedOrders()).isOne();

        var before = jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE order_source='IMPORT'", Integer.class);
        var invalid = orderImports.importFile(
                deliveryNote("不存在的收货单位" + prefix, List.<String[]>of(noteLine(standard, "0"))), prefix + "bad", true);
        assertThat(invalid.getTotalErrors()).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE order_source='IMPORT'", Integer.class)).isEqualTo(before);
    }

    /** 返回 {@code [商品名称, 规格, 单位]}，送货单只写名称与规格、单位必须与销售单位一致。 */
    private String[] productOf(Long skuId) {
        var row = jdbc.queryForMap(
                "SELECT p.name, s.spec_name, s.sale_unit FROM xsy_product_sku s JOIN xsy_product_spu p ON p.id = s.spu_id WHERE s.id = ?",
                skuId);
        return new String[]{(String) row.get("name"), (String) row.get("spec_name"), (String) row.get("sale_unit")};
    }

    private String[] noteLine(String[] product, String quantity) {
        return new String[]{product[0], product[1], product[2], quantity};
    }

    /** 生成客户版式的送货单：第 3 行收货单位/日期，第 4 行表头，明细从第 5 行开始，末行合计与签字栏。 */
    private org.springframework.mock.web.MockMultipartFile deliveryNote(String customerName, List<String[]> lines)
            throws Exception {
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                var bytes = new java.io.ByteArrayOutputStream()) {
            var sheet = book.createSheet("送货单");
            sheet.createRow(0).createCell(0).setCellValue("鲜蔬源(西安)供应链服务有限公司");
            sheet.createRow(1).createCell(0).setCellValue("送货单");
            var meta = sheet.createRow(2);
            meta.createCell(0).setCellValue("收货单位：");
            meta.createCell(1).setCellValue(customerName);
            meta.createCell(6).setCellValue("日期：");
            meta.createCell(7).setCellValue("2026-10-27");
            var headers = List.of("序号", "商品名称", "单位", "规格", "实数量", "销售单价", "销售金额", "备注");
            var header = sheet.createRow(3);
            for (int column = 0; column < headers.size(); column++)
                header.createCell(column).setCellValue(headers.get(column));
            for (int index = 0; index < lines.size(); index++) {
                var values = lines.get(index);
                var line = sheet.createRow(4 + index);
                line.createCell(0).setCellValue(index + 1);
                line.createCell(1).setCellValue(values[0]);
                line.createCell(2).setCellValue(values[2]);
                line.createCell(3).setCellValue(values[1]);
                line.createCell(4).setCellValue(Double.parseDouble(values[3]));
            }
            sheet.createRow(4 + lines.size()).createCell(0).setCellValue("合计金额");
            var footer = sheet.createRow(5 + lines.size());
            footer.createCell(1).setCellValue("销售方：");
            footer.createCell(7).setCellValue("收货方：");
            book.write(bytes);
            return new org.springframework.mock.web.MockMultipartFile("file", "delivery-note.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void lateImportFailureRollsBackOrdersItemsAddressesLogsAndIdempotency() {
        var customerId = customer();
        var standardSku = newOnShelfSku("ROLLBACK_OK");
        var unavailableSku = newProductSku("ROLLBACK_BAD", "ON_SHELF", "OFF_SHELF");
        jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", standardSku);
        var first = form(customerId, standardSku);
        first.setOrderSource("IMPORT");
        var second = form(customerId, unavailableSku);
        second.setOrderSource("IMPORT");
        var logCount = jdbc.queryForObject("SELECT count(*) FROM xsy_order_operation_log", Long.class);
        try {
            assertThatThrownBy(() -> orders.importOrders(List.of(first, second), "test-hash", prefix + "rollback", 2))
                    .isInstanceOfSatisfying(SalesOrderService.ImportOrderException.class, e -> assertThat(e.getOrderIndex()).isEqualTo(1));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE customer_id=?", Integer.class, customerId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order_item WHERE sku_id IN (?,?)", Integer.class, standardSku, unavailableSku)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_address_snapshot WHERE customer_id=?", Integer.class, customerId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_operation_log", Long.class)).isEqualTo(logCount);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key=?", Integer.class, prefix + "rollback")).isZero();
        } finally {
            // Only this test's fixture IDs are removed; no shared business data is touched.
            var spuIds = jdbc.queryForList("SELECT spu_id FROM xsy_product_sku WHERE id IN (?,?)", Long.class, standardSku, unavailableSku);
            jdbc.update("DELETE FROM xsy_product_sku WHERE id IN (?,?)", standardSku, unavailableSku);
            for (var spuId : spuIds) jdbc.update("DELETE FROM xsy_product_spu WHERE id=?", spuId);
            jdbc.update("DELETE FROM xsy_customer WHERE id=?", customerId);
        }
    }

    @Test
    void mixedManualOrderConfirmsWithProvisionalWeightRowAmount() {
        var customerId = customer();
        var standardSku = newOnShelfSku("MIX_STANDARD");
        var weightSku = newOnShelfSku("MIX_WEIGHT");
        jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", standardSku);
        session.clearCache();
        var request = form(customerId, standardSku);
        request.getItems().add(form(customerId, weightSku).getItems().getFirst());
        var order = orders.createAndProgress(request, prefix + "mixed");
        assertThat(order.getStatus()).isEqualTo("PENDING");
        assertThat(order.getItems()).filteredOn(item -> item.getSkuId().equals(standardSku)).singleElement()
                .satisfies(item -> assertThat(item.getActualQuantity()).isEqualByComparingTo("2.0000"));
        assertThat(order.getItems()).filteredOn(item -> item.getSkuId().equals(weightSku)).singleElement()
                .satisfies(item -> assertThat(item.getActualQuantity()).isNull());
        // 称重行未称也能确认：实数量保持空，结算额按订购量暂计，等分拣台称完再回写
        var confirmed = orders.confirm(version(order), prefix + "early-confirm");
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.getItems()).filteredOn(item -> item.getSkuId().equals(weightSku)).singleElement()
                .satisfies(item -> {
                    assertThat(item.getActualQuantity()).isNull();
                    assertThat(item.getSettlementLineAmount()).isEqualByComparingTo(item.getOrderedLineAmount());
                });
    }

    @Test
    void zeroIsPricedStandardQuantityAndIdempotentReplay() {
        var c = customer();
        var sku = newOnShelfSku("Z");
        jdbc.update("UPDATE xsy_product_sku SET market_price=0,product_type='STANDARD' WHERE id=?", sku);
        var f = form(c, sku);
        var o = orders.create(f, prefix);
        assertThat(orders.create(f, prefix).getOrderId()).isEqualTo(o.getOrderId());
        f.setRemark("changed");
        expectCode(() -> orders.create(f, prefix), 40966);
        var request = version(o);
        o = orders.submit(request, prefix + "s");
        assertThat(orders.submit(request, prefix + "s").getVersion()).isEqualTo(o.getVersion());
        assertThat(o.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("2.0000");
        var actual = new OrderActualQuantityForm();
        actual.setOrderId(o.getOrderId());
        actual.setItemId(o.getItems().getFirst().getItemId());
        actual.setVersion(o.getItems().getFirst().getVersion());
        actual.setActualQuantity("1.0000");
        actual.setReason("不能改标品");
        expectCode(() -> orders.actualQuantity(actual, prefix + "invalidactual"), 40962);
        o = orders.confirm(version(o), prefix + "f");
        assertThat(o.getSettlementTotalAmount()).isEqualByComparingTo("0.0000");
    }

    @Test
    void manualOverrideDoesNotBypassVisibilityAndDraftCanClearIt() {
        var c = customer();
        var sku = newOnShelfSku("M");
        var f = form(c, sku);
        var item = f.getItems().getFirst();
        item.setManualPriceOverride(true);
        item.setUnitPrice("4.5000");
        item.setOverrideReason("协商");
        var o = orders.create(f, prefix);
        assertThat(o.getItems().getFirst().getDraftPriceSource()).isEqualTo("OVERRIDE");
        jdbc.update("UPDATE xsy_customer SET visibility_policy='ALLOWLIST' WHERE id=?", c);
        session.clearCache();
        var draft = o;
        expectCode(() -> orders.submit(version(draft), prefix + "s"), 40949);
    }

    @Test
    void afterSalesQuotaApprovalAndRefundAreAtomic() {
        var o = confirmed(customer(), newOnShelfSku("R"));
        var f = new OrderReturnAddForm();
        f.setOrderId(o.getOrderId());
        f.setReason("品质问题");
        var row = new OrderReturnItemForm();
        row.setOrderItemId(o.getItems().getFirst().getItemId());
        row.setRequestedQuantity("1.0000");
        f.setItems(List.of(row));
        var r = returns.create(f, prefix + "r");
        expectCode(() -> returns.create(f, prefix + "r2"), 40969);
        var approve = new OrderReturnApproveForm();
        approve.setReturnId(r.getReturnId());
        approve.setVersion(r.getVersion());
        var approved = new OrderReturnApproveItemForm();
        approved.setOrderItemId(row.getOrderItemId());
        approved.setApprovedQuantity("0.5000");
        approve.setItems(List.of(approved));
        r = returns.approve(approve, prefix + "approve");
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        var q = new OrderRefundQueryForm();
        q.setOrderId(o.getOrderId());
        q.setPageNum(1L);
        q.setPageSize(20L);
        var refund = refunds.query(q).getList().getFirst();
        assertThat(refund.getRefundAmount()).isEqualByComparingTo("0.6000");
        var complete = new OrderRefundCompleteForm();
        complete.setRefundId(refund.getRefundId());
        complete.setVersion(refund.getVersion());
        complete.setExternalReference(prefix);
        assertThat(refunds.complete(complete, prefix + "complete").getStatus()).isEqualTo("COMPLETED");
        assertThat(refunds.complete(complete, prefix + "complete").getStatus()).isEqualTo("COMPLETED");
        expectCode(() -> refunds.complete(complete, prefix + "staleRefund"), 40921);
        expectCode(() -> returns.approve(approve, prefix + "staleReturn"), 40921);
        assertThat(query.detail(o.getOrderId()).getStatus()).isEqualTo("CONFIRMED");

        // return / refund 与 cancellation 一样要留操作日志。建单 + 审批 = 两条 RETURN，
        // 退款只有一条 REFUND —— 上面那笔 complete 用同一个 Idempotency-Key 调了两次，
        // 第二次是重放，因此日志条数同时钉住「重放不重复记日志」。
        assertThat(logCount(o.getOrderId(), "RETURN")).isEqualTo(2);
        assertThat(logCount(o.getOrderId(), "REFUND")).isEqualTo(1);
    }

    @Test
    @DisplayName("订单已有资金分摊计划时，退款完成必须让位给分摊路径而不是整笔回滚")
    void refundCompletionSurvivesAnExistingAllocationPlan() {
        var o = confirmed(customer(), newOnShelfSku("AP"));
        var f = new OrderReturnAddForm();
        f.setOrderId(o.getOrderId());
        f.setReason("品质问题");
        var row = new OrderReturnItemForm();
        row.setOrderItemId(o.getItems().getFirst().getItemId());
        row.setRequestedQuantity("1.0000");
        f.setItems(List.of(row));
        var r = returns.create(f, prefix + "ap-return");
        var approve = new OrderReturnApproveForm();
        approve.setReturnId(r.getReturnId());
        approve.setVersion(r.getVersion());
        var approved = new OrderReturnApproveItemForm();
        approved.setOrderItemId(row.getOrderItemId());
        approved.setApprovedQuantity("0.5000");
        approve.setItems(List.of(approved));
        returns.approve(approve, prefix + "ap-approve");

        var q = new OrderRefundQueryForm();
        q.setOrderId(o.getOrderId());
        q.setPageNum(1L);
        q.setPageSize(20L);
        var refund = refunds.query(q).getList().getFirst();

        // 模拟「该订单此前已按新分摊路径退过款」：分摊计划只可能来自更早那笔已完成的退款，
        // 而旧钱包返还路径看到计划就必须整体让位（否则同一笔本金会被退两次）。
        Long customerId = jdbc.queryForObject("SELECT customer_id FROM xsy_sales_order WHERE id = ?",
                Long.class, o.getOrderId());
        Long settlementCustomerId = jdbc.queryForObject(
                "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, o.getOrderId());
        jdbc.update("INSERT INTO xsy_refund_funding_allocation (order_refund_id, order_id, customer_id, "
                + "settlement_customer_id, source_method, source_id, source_principal, allocated_amount, status, "
                + "source_succeeded_at, sort_order, created_by, updated_by) "
                + "VALUES (?, ?, ?, ?, 'BALANCE', 1, 1.0000, 1.0000, 'PENDING', CURRENT_TIMESTAMP, 0, '1:1', '1:1')",
                refund.getRefundId(), o.getOrderId(), customerId, settlementCustomerId);

        // 完成必须成功：让位要发生在这里的前置分流，不能靠兜异常 —— refundOnCompletion 是
        // MANDATORY 传播，异常一旦逃出就会把本事务标记成 rollback-only，这笔退款完成会整笔回滚。
        var complete = new OrderRefundCompleteForm();
        complete.setRefundId(refund.getRefundId());
        complete.setVersion(refund.getVersion());
        complete.setExternalReference(prefix);
        assertThat(refunds.complete(complete, prefix + "ap-complete").getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void rejectedReturnIsAuditableOnTheOrder() {
        var o = confirmed(customer(), newOnShelfSku("RJ"));
        var f = new OrderReturnAddForm();
        f.setOrderId(o.getOrderId());
        f.setReason("错发");
        var row = new OrderReturnItemForm();
        row.setOrderItemId(o.getItems().getFirst().getItemId());
        row.setRequestedQuantity("1.0000");
        f.setItems(List.of(row));
        var r = returns.create(f, prefix + "rj");
        var decision = new OrderReturnDecisionForm();
        decision.setReturnId(r.getReturnId());
        decision.setVersion(r.getVersion());
        decision.setDecisionReason("不在受理范围");
        assertThat(returns.reject(decision, prefix + "reject").getStatus()).isEqualTo("REJECTED");
        assertThat(logCount(o.getOrderId(), "RETURN")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_order_operation_log WHERE order_id = ? AND operation_type = 'RETURN'"
                        + " AND after_data ->> 'status' = 'REJECTED'",
                Integer.class, o.getOrderId())).as("驳回这条要把状态迁移记进日志").isEqualTo(1);
    }

    private int logCount(Long orderId, String operation) {
        return jdbc.queryForObject("SELECT count(*) FROM xsy_order_operation_log WHERE order_id = ?"
                + " AND operation_type = ?", Integer.class, orderId, operation);
    }

    @Test
    void cancelAndDraftDeletionRespectStateAndVersions() {
        var c = customer();
        var sku = newOnShelfSku("D");
        var o = orders.create(form(c, sku), prefix);
        var deleteCustomer = new CustomerDeleteForm();
        deleteCustomer.setCustomerId(c);
        deleteCustomer.setVersion(1);
        expectCode(() -> customers.delete(deleteCustomer), 40939);
        orders.delete(version(o));
        assertThat(jdbc.queryForObject("SELECT deleted FROM xsy_sales_order WHERE id=?", Boolean.class, o.getOrderId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order_item WHERE order_id=? AND NOT deleted", Integer.class, o.getOrderId())).isZero();
        orders.delete(version(o));
        o = orders.create(form(c, sku), prefix + "other");
        var cancel = new OrderCancelForm();
        cancel.setOrderId(o.getOrderId());
        cancel.setVersion(o.getVersion());
        cancel.setReason("客户取消");
        assertThat(orders.cancel(cancel, prefix + "cancel").getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentDuplicateCreatesAndReturnQuotaHaveOneWinner() throws Exception {
        var c = customer();
        var sku = newOnShelfSku("CONCURRENT");
        var f = form(c, sku);
        // 本用例真提交：键必须每次运行唯一，否则重跑会撞上上一次留下的幂等记录（请求指纹必然不同）。
        var sameKey = prefix + "same-" + UUID.randomUUID();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            java.util.concurrent.Callable<SalesOrderDetailVO> create = () -> {
                // 每个线程独立请求体：共用同一个对象时，写入路径对它的规范化会让另一线程算出的指纹漂移。
                var request = form(c, sku);
                operator();
                try {
                    barrier.await();
                    return orders.create(request, sameKey);
                } finally {
                    net.lab1024.sa.base.common.util.SmartRequestUtil.remove();
                }
            };
            var first = pool.submit(create);
            var second = pool.submit(create);
            assertThat(first.get().getOrderId()).isEqualTo(second.get().getOrderId());
            var o = confirmed(c, sku);
            var r = new OrderReturnAddForm();
            r.setOrderId(o.getOrderId());
            r.setReason("并发退货");
            var i = new OrderReturnItemForm();
            i.setOrderItemId(o.getItems().getFirst().getItemId());
            i.setRequestedQuantity("1.0000");
            r.setItems(List.of(i));
            java.util.concurrent.Callable<Integer> attempt = () -> {
                operator();
                try {
                    barrier.await();
                    returns.create(r, UUID.randomUUID().toString());
                    return 0;
                } catch (com.xsy.scm.common.exception.ScmBusinessException e) {
                    return e.getErrorCode().getCode();
                } finally {
                    net.lab1024.sa.base.common.util.SmartRequestUtil.remove();
                }
            };
            var one = pool.submit(attempt);
            var two = pool.submit(attempt);
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(0, 40969);
        } finally {
            pool.shutdownNow();
        }
    }

    private void operator() {
        var e = new net.lab1024.sa.admin.module.system.login.domain.RequestEmployee();
        e.setEmployeeId(1L);
        e.setActualName("W4 IT");
        e.setUserType(net.lab1024.sa.base.common.enumeration.UserTypeEnum.ADMIN_EMPLOYEE);
        net.lab1024.sa.base.common.util.SmartRequestUtil.setRequestUser(e);
    }

    @Test
    void snapshotsOrderingManualLockAndItemVersionArePreserved() {
        var c = customer();
        var first = newOnShelfSku("S1");
        var second = newOnShelfSku("S2");
        var f = form(c, first);
        var i = f.getItems().getFirst();
        i.setSortOrder(4);
        i.setManualPriceOverride(true);
        i.setUnitPrice("2.1234");
        i.setOverrideReason("客户协商");
        var j = new SalesOrderItemForm();
        j.setSkuId(second);
        j.setOrderedQuantity("1.0000");
        j.setManualPriceOverride(false);
        j.setSortOrder(1);
        f.getItems().add(j);
        var o = orders.create(f, prefix);
        assertThat(o.getItems()).extracting(SalesOrderItemVO::getSkuId).containsExactly(second, first);
        assertThat(o.getItems().getFirst().getSpecValuesSnapshot()).isNotEmpty();
        assertThat(o.getItems().getFirst().getSaleUnitSnapshot()).isEqualTo("kg");
        o = orders.submit(version(o), prefix + "submit");
        var manual = o.getItems().getLast();
        assertThat(manual.getLockedUnitPrice()).isEqualByComparingTo("2.1234");
        assertThat(manual.getLockedPriceSource()).isEqualTo("OVERRIDE");
        var a = new OrderActualQuantityForm();
        a.setOrderId(o.getOrderId());
        a.setItemId(manual.getItemId());
        a.setVersion(999);
        a.setActualQuantity("1.0000");
        a.setReason("实测");
        expectCode(() -> orders.actualQuantity(a, prefix + "bad"), 40965);
        assertThat(o.getAddress().getAddress()).isEqualTo("测试地址");
    }

    @Test
    void rejectedAndCancelledReturnsReleaseQuotaAndStaleApprovalFails() {
        var o = confirmed(customer(), newOnShelfSku("DECIDE"));
        var f = new OrderReturnAddForm();
        f.setOrderId(o.getOrderId());
        f.setReason("退货");
        var i = new OrderReturnItemForm();
        i.setOrderItemId(o.getItems().getFirst().getItemId());
        i.setRequestedQuantity("1.0000");
        f.setItems(List.of(i));
        var r = returns.create(f, prefix + "r1");
        var decision = new OrderReturnDecisionForm();
        decision.setReturnId(r.getReturnId());
        decision.setVersion(999);
        decision.setDecisionReason("审核");
        expectCode(() -> returns.reject(decision, prefix + "stale"), 40921);
        decision.setVersion(r.getVersion());
        assertThat(returns.reject(decision, prefix + "reject").getStatus()).isEqualTo("REJECTED");
        r = returns.create(f, prefix + "r2");
        decision.setReturnId(r.getReturnId());
        decision.setVersion(r.getVersion());
        assertThat(returns.cancel(decision, prefix + "cancel").getStatus()).isEqualTo("CANCELLED");
        assertThat(returns.create(f, prefix + "r3").getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("商品行备注落库并按行读回：修改能覆盖、清空能生效，不写进整单备注")
    void itemRemarkRoundTripsPerLine() {
        var c = customer();
        var f = form(c, newOnShelfSku("IREMARK"));
        f.getItems().getFirst().setRemark("去皮");
        var o = orders.create(f, prefix + "ir-create");

        // 读的是实体 resultMap：行备注必须显式映射，否则 SELECT * 取回也落不到 VO
        assertThat(o.getItems().getFirst().getRemark()).isEqualTo("去皮");
        assertThat(o.getRemark()).as("整单备注另存").isEqualTo("测试订单");

        var u = new SalesOrderUpdateForm();
        org.springframework.beans.BeanUtils.copyProperties(f, u);
        u.setOrderId(o.getOrderId());
        u.setVersion(o.getVersion());
        u.getItems().getFirst().setItemId(o.getItems().getFirst().getItemId());
        u.getItems().getFirst().setVersion(o.getItems().getFirst().getVersion());
        u.getItems().getFirst().setRemark("切段、分袋");
        var updated = orders.update(u);
        assertThat(updated.getItems().getFirst().getRemark()).isEqualTo("切段、分袋");

        u.setVersion(updated.getVersion());
        u.getItems().getFirst().setVersion(updated.getItems().getFirst().getVersion());
        u.getItems().getFirst().setRemark(null);
        assertThat(orders.update(u).getItems().getFirst().getRemark()).as("清空行备注要真的清掉").isNull();
        assertThat(query.detail(o.getOrderId()).getRemark()).isEqualTo("测试订单");
    }
}
