package com.xsy.scm.print;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.print.domain.form.ScmPrintActionForm;
import com.xsy.scm.print.domain.form.ScmPrintBatchForm;
import com.xsy.scm.print.domain.form.ScmPrintCustomerTemplateForm;
import com.xsy.scm.print.domain.form.ScmPrintTemplateForm;
import com.xsy.scm.print.domain.model.ScmPrintTemplateModel;
import com.xsy.scm.print.domain.vo.ScmPrintBatchResultVO;
import com.xsy.scm.print.domain.vo.ScmPrintRenderVO;
import com.xsy.scm.print.service.CustomerSheetExcelService;
import com.xsy.scm.print.service.ScmPrintBatchService;
import com.xsy.scm.print.service.ScmPrintCustomerBindingService;
import com.xsy.scm.print.service.ScmPrintService;
import com.xsy.scm.print.service.ScmPrintTemplateService;
import com.xsy.scm.print.support.CustomerDeliveryNotePrintSourceProvider;
import com.xsy.scm.print.support.SalesOrderPrintSourceProvider;
import com.xsy.scm.print.support.ScmPrintSource;
import com.xsy.scm.print.support.ScmPrintTemplateValidator;
import com.xsy.scm.print.support.StatementPrintSourceProvider;
import com.xsy.scm.promotion.domain.vo.OrderDiscountVO;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.dao.ScmCustomerStatementDao;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementVO;
import com.xsy.scm.report.service.ScmCustomerStatementService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintTest {

    @Nested
    class PrintUnitTest {
        @Nested

        class CustomerDeliveryDiscountTest {
            @Test
            void discountFollowsActualQuantityForFullPartialZeroAndUnsortedDelivery() {
                var query = mock(SalesOrderQueryService.class);
                var order = new SalesOrderDetailVO();
                order.setOrderId(1L);
                order.setSettlementTotalAmount(new BigDecimal("100"));
                var line = new SalesOrderItemVO();
                line.setItemId(2L);
                line.setOrderedQuantity(new BigDecimal("10"));
                line.setLockedUnitPrice(new BigDecimal("10"));
                line.setOrderedLineAmount(new BigDecimal("100"));
                line.setSettlementLineAmount(new BigDecimal("100"));
                order.setItems(List.of(line));
                var discount = new OrderDiscountVO();
                var allocation = new OrderDiscountVO.Allocation();
                allocation.setOrderItemId(2L);
                allocation.setDiscountAmount(new BigDecimal("10"));
                discount.setAllocations(List.of(allocation));
                order.setDiscount(discount);
                when(query.detail(1L)).thenReturn(order);
                var provider = new CustomerDeliveryNotePrintSourceProvider(query);
                when(query.sortedQuantities(1L)).thenReturn(Map.of(2L, new BigDecimal("10")));
                assertThat(provider.load(1L).totalValues().get("settlementTotalAmount")).isEqualTo("90.0000");
                when(query.sortedQuantities(1L)).thenReturn(Map.of(2L, new BigDecimal("5")));
                assertThat(provider.load(1L).rows().getFirst().get("settlementLineAmount")).isEqualTo("45.0000");
                when(query.sortedQuantities(1L)).thenReturn(Map.of(2L, BigDecimal.ZERO));
                assertThat(provider.load(1L).totalValues().get("settlementTotalAmount")).isEqualTo("0.0000");
                when(query.sortedQuantities(1L)).thenReturn(Map.of());
                assertThat(provider.load(1L).totalValues().get("settlementTotalAmount")).isEqualTo("90.0000");
            }
        }

        @Nested

        class CustomerSheetExcelServiceTest {
            @Test
            void orderAndDeliveryUseDifferentQuantitiesAndNumericCells() throws Exception {
                var query = mock(SalesOrderQueryService.class);
                var order = new SalesOrderDetailVO();
                order.setCustomerNameSnapshot("测试客户");
                order.setExpectDeliveryTime(OffsetDateTime.parse("2026-11-05T00:00:00+08:00"));
                var line = new SalesOrderItemVO();
                line.setItemId(2L);
                line.setProductNameSnapshot("上海青");
                line.setSaleUnitSnapshot("斤");
                line.setOrderedQuantity(new BigDecimal("20"));
                line.setLockedUnitPrice(new BigDecimal("2.45"));
                line.setRemark("分两袋");
                order.setItems(List.of(line));
                when(query.detail(1L)).thenReturn(order);
                when(query.sortedQuantities(1L)).thenReturn(Map.of(2L, new BigDecimal("20.5")));
                var service = new CustomerSheetExcelService(query);
                for (boolean delivery : new boolean[] {false, true}) {
                    try (var book = WorkbookFactory.create(new ByteArrayInputStream(service.export(1L, delivery)))) {
                        var sheet = book.getSheetAt(0);
                        assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("测试客户");
                        assertThat(sheet.getRow(2).getCell(7).getStringCellValue()).isEqualTo("2026-11-05");
                        assertThat(sheet.getRow(4).getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
                        assertThat(sheet.getRow(4).getCell(4).getNumericCellValue()).isEqualTo(delivery ? 20.5 : 20);
                        assertThat(sheet.getRow(4).getCell(6).getNumericCellValue()).isEqualTo(delivery ? 50.225 : 49);
                        assertThat(sheet.getRow(4).getCell(7).getStringCellValue()).isEqualTo("分两袋");
                        assertThat(sheet.getRow(66).getCell(6).getNumericCellValue()).isEqualTo(delivery ? 50.225 : 49);
                        assertThat(sheet.getRow(67).getCell(1).getStringCellValue()).contains("销售方");
                        assertThat(sheet.getRow(67).getCell(7).getStringCellValue()).contains("收货方");
                    }
                }
                when(query.sortedQuantities(1L)).thenReturn(Map.of());
                try (var book = WorkbookFactory.create(new ByteArrayInputStream(service.export(1L, true)))) {
                    assertThat(book.getSheetAt(0).getRow(4).getCell(4).getCellType()).isEqualTo(CellType.BLANK);
                    assertThat(book.getSheetAt(0).getRow(66).getCell(6).getCellType()).isEqualTo(CellType.BLANK);
                }
                when(query.sortedQuantities(1L)).thenReturn(Map.of(2L, BigDecimal.ZERO));
                try (var book = WorkbookFactory.create(new ByteArrayInputStream(service.export(1L, true)))) {
                    assertThat(book.getSheetAt(0).getRow(4).getCell(4).getNumericCellValue()).isZero();
                    assertThat(book.getSheetAt(0).getRow(66).getCell(6).getNumericCellValue()).isZero();
                }
                when(query.detail(1L)).thenThrow(new ScmDataScopeException());
                assertThatThrownBy(() -> service.export(1L, false)).isInstanceOf(ScmDataScopeException.class);
            }

            @Test
            void longOrderMovesTotalAndSignaturesWithoutDroppingItems() throws Exception {
                var query = mock(SalesOrderQueryService.class);
                var order = new SalesOrderDetailVO();
                order.setItems(java.util.stream.IntStream.range(0, 70).mapToObj(index -> {
                    var item = new SalesOrderItemVO();
                    item.setItemId((long) index + 1);
                    item.setProductNameSnapshot("商品" + (index + 1));
                    item.setSaleUnitSnapshot("斤");
                    item.setOrderedQuantity(BigDecimal.ONE);
                    item.setLockedUnitPrice(BigDecimal.ONE);
                    return item;
                }).toList());
                when(query.detail(1L)).thenReturn(order);
                try (var book = WorkbookFactory.create(new ByteArrayInputStream(new CustomerSheetExcelService(query).export(1L, false)))) {
                    var sheet = book.getSheetAt(0);
                    assertThat(sheet.getRow(73).getCell(1).getStringCellValue()).isEqualTo("商品70");
                    assertThat(sheet.getRow(74).getCell(6).getNumericCellValue()).isEqualTo(70);
                    assertThat(sheet.getRow(75).getCell(1).getStringCellValue()).contains("销售方");
                    assertThat(sheet.getRow(75).getCell(7).getStringCellValue()).contains("收货方");
                }
            }
        }

        @Nested

        class SalesOrderPrintContractTest {
            @Test
            void salesOrderUsesTheExistingScopedOrderQueryPermission() {
                var provider = new SalesOrderPrintSourceProvider(null);
                assertThat(provider.documentType()).isEqualTo(ScmPrintDocumentTypeEnum.SALES_ORDER);
                assertThat(provider.queryPermission()).isEqualTo(OrderPermission.QUERY);
            }

            @Test
            void customerBindingUsesTheScopedOrderDetailCustomer() {
                SalesOrderQueryService orderQueryService = mock(SalesOrderQueryService.class);
                SalesOrderDetailVO detail = new SalesOrderDetailVO();
                detail.setCustomerId(42L);
                when(orderQueryService.detail(7L)).thenReturn(detail);

                var provider = new SalesOrderPrintSourceProvider(orderQueryService);

                assertThat(provider.customerId(7L)).isEqualTo(42L);
                verify(orderQueryService).detail(7L);
            }

            @Test
            void templateAcceptsKnownFieldsAndRejectsUnlistedPriceOrExpressionKeys() {
                var type = ScmPrintDocumentTypeEnum.SALES_ORDER;
                var model = new ScmPrintTemplateModel();
                model.setTitle("销售订单");
                model.setPaper("A4");
                model.setOrientation("PORTRAIT");
                model.setHeaderFields(List.of("customerName", "orderNo"));
                model.setColumns(List.of("productName", "lockedUnitPrice", "orderedQuantity"));
                model.setShowTotals(true);
                assertThat(ScmPrintTemplateValidator.validate(type, model).getColumns())
                        .containsExactly("productName", "lockedUnitPrice", "orderedQuantity");

                model.setColumns(List.of("productName", "customerSecretExpression"));
                assertThatThrownBy(() -> ScmPrintTemplateValidator.validate(type, model))
                        .isInstanceOf(com.xsy.scm.common.exception.ScmBusinessException.class);
            }

            @Test
            void monetaryFieldsAreExplicitlyClassifiedAndOriginalStatesAreKeptDistinct() {
                var type = ScmPrintDocumentTypeEnum.SALES_ORDER;
                assertThat(type.column("draftUnitPrice").money()).isTrue();
                assertThat(type.column("lockedUnitPrice").money()).isTrue();
                assertThat(type.column("orderedQuantity").money()).isFalse();
                assertThat(type.total("orderedTotalAmount").money()).isTrue();
                assertThat(type.total("settlementTotalAmount").money()).isTrue();
                assertThat(type.column("lockedUnitPrice").label()).isNotEqualTo(type.column("draftUnitPrice").label());
            }

            @Test
            void customerDeliveryTemplateCanDisplayOrderedAndActualQuantitiesTogether() {
                var model = new ScmPrintTemplateModel();
                model.setTitle("客户送货单");
                model.setPaper("A4");
                model.setOrientation("PORTRAIT");
                model.setHeaderFields(List.of("customerName"));
                model.setColumns(List.of("productName", "orderedQuantity", "actualQuantity", "lockedUnitPrice"));
                assertThat(ScmPrintTemplateValidator.validate(ScmPrintDocumentTypeEnum.CUSTOMER_DELIVERY_NOTE, model).getColumns())
                    .containsExactly("productName", "orderedQuantity", "actualQuantity", "lockedUnitPrice");
            }
        }

        @Nested

        class StatementPrintContractTest {
            @Test
            void statementUsesTheExistingStatementQueryPermissionAndFrozenVersionRead() {
                var provider = new StatementPrintSourceProvider(null);

                assertThat(provider.documentType()).isEqualTo(ScmPrintDocumentTypeEnum.STATEMENT);
                assertThat(provider.queryPermission()).isEqualTo(ScmReportPermission.CUSTOMER_STATEMENT_QUERY);
            }

            @Test
            void statementCatalogMarksEveryMoneyFieldAndSupportsCustomerBinding() {
                var type = ScmPrintDocumentTypeEnum.STATEMENT;

                assertThat(type.requiresMoneyPermission()).isTrue();
                assertThat(type.headerField("openingReceivable").money()).isTrue();
                assertThat(type.headerField("closingReceivable").money()).isTrue();
                assertThat(type.headerField("settlementCustomerName").money()).isFalse();
                assertThat(type.column("receivableDelta").money()).isTrue();
                assertThat(type.column("receiptDelta").money()).isTrue();
                assertThat(type.column("writeOffDelta").money()).isTrue();
                assertThat(type.column("refundDelta").money()).isTrue();
                assertThat(type.column("receivableBalance").money()).isTrue();
                assertThat(type.column("factType").money()).isFalse();
                assertThat(type.total("closingReceivable").money()).isTrue();
                assertThat(type.supportsCustomerBinding()).isTrue();
                assertThat(ScmPrintDocumentTypeEnum.SALES_ORDER.supportsCustomerBinding()).isTrue();
                assertThat(ScmPrintDocumentTypeEnum.DELIVERY_NOTE.supportsCustomerBinding()).isFalse();
                assertThat(ScmPrintDocumentTypeEnum.SORTING_TICKET.supportsCustomerBinding()).isFalse();
            }

            @Test
            void frozenStatementBecomesAPrintSourceWithoutRecalculatingMoney() {
                ScmCustomerStatementService service = mock(ScmCustomerStatementService.class);
                when(service.loadForPrint(9L)).thenReturn(statement());

                var provider = new StatementPrintSourceProvider(service);
                assertThat(provider.customerId(9L)).isEqualTo(88L);

                ScmPrintSource source = provider.load(9L);
                assertThat(source.documentNo()).isEqualTo("#9");
                assertThat(source.headerValues())
                    .containsEntry("settlementCustomerName", "测试结算方")
                    .containsEntry("period", "2026-10-01 至 2026-10-31")
                    .containsEntry("openingReceivable", "1.0000")
                    .containsEntry("closingReceivable", "2.0000")
                    .containsEntry("generatedAt", "2026-10-31 10:00:00")
                    .containsEntry("statementVersion", "9")
                    .containsEntry("partialScope", "完整结算方");
                assertThat(source.rows()).hasSize(1);
                assertThat(source.rows().getFirst())
                    .containsEntry("eventAt", "2026-10-20 09:30:00")
                    .containsEntry("factType", "实际收款")
                    .containsEntry("documentNo", "SK-001")
                    .containsEntry("relatedNo", "SO-001")
                    .containsEntry("receivableBalance", "2.0000");
                assertThat(source.totalValues())
                    .containsEntry("closingReceivable", "2.0000")
                    .containsEntry("receivableIncrease", "3.0000")
                    .containsEntry("receiptNet", "1.0000");
            }

            @Test
            void printReadDoesNotRequireTheGeneratorButStillRequiresVisibleSources() {
                ScmCustomerStatementDao statementDao = mock(ScmCustomerStatementDao.class);
                ScmDataScopeService dataScopeService = mock(ScmDataScopeService.class);
                ScmDataScopeContext scope = scope();
                when(dataScopeService.resolve()).thenReturn(scope);

                ScmCustomerStatementVO header = statement();
                // 生成人是别人：普通详情会拒绝，打印读取应只看当前数据范围
                header.setGeneratedByEmployeeId(999L);
                when(statementDao.selectStatementById(9L)).thenReturn(header);
                when(statementDao.selectItems(9L)).thenReturn(header.getItems());
                when(statementDao.selectEvents(any(), any(), any(), any(), anyInt())).thenReturn(header.getItems());
                when(statementDao.selectSources(9L)).thenReturn(header.getItems());

                var service = new ScmCustomerStatementService(statementDao, dataScopeService);
                ScmCustomerStatementVO loaded = service.loadForPrint(9L);

                assertThat(loaded.getId()).isEqualTo(9L);
                verify(statementDao, never()).selectStatement(any(), any());
            }

            @Test
            void printReadRejectsWhenAnySourceFactIsNoLongerVisible() {
                ScmCustomerStatementDao statementDao = mock(ScmCustomerStatementDao.class);
                ScmDataScopeService dataScopeService = mock(ScmDataScopeService.class);
                ScmDataScopeContext scope = scope();
                when(dataScopeService.resolve()).thenReturn(scope);

                ScmCustomerStatementVO header = statement();
                when(statementDao.selectStatementById(9L)).thenReturn(header);
                when(statementDao.selectItems(9L)).thenReturn(header.getItems());
                when(statementDao.selectEvents(any(), any(), any(), any(), anyInt()))
                    .thenReturn(List.of(fact("RECEIPT", 100L)));
                when(statementDao.selectSources(9L)).thenReturn(List.of(fact("RECEIPT", 100L), fact("RECEIPT", 200L)));

                var service = new ScmCustomerStatementService(statementDao, dataScopeService);

                assertThatThrownBy(() -> service.loadForPrint(9L)).isInstanceOf(ScmBusinessException.class);
            }

            private static ScmDataScopeContext scope() {
                ScmDataScopeContext scope = mock(ScmDataScopeContext.class);
                when(scope.getEmployeeId()).thenReturn(7L);
                when(scope.getWarehouseScope()).thenReturn(ScmValueScope.all());
                when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.all());
                when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.all());
                return scope;
            }

            private static ScmCustomerStatementItemVO fact(String factType, Long factId) {
                ScmCustomerStatementItemVO item = new ScmCustomerStatementItemVO();
                item.setFactType(factType);
                item.setFactId(factId);
                return item;
            }

            private static ScmCustomerStatementVO statement() {
                ScmCustomerStatementVO statement = new ScmCustomerStatementVO();
                statement.setId(9L);
                statement.setSettlementCustomerId(88L);
                statement.setSettlementCustomerName("测试结算方");
                statement.setStartDate(LocalDate.of(2026, 10, 1));
                statement.setEndDate(LocalDate.of(2026, 10, 31));
                statement.setPartialScope(false);
                statement.setGeneratedAt(OffsetDateTime.of(2026, 10, 31, 10, 0, 0, 0, ZoneOffset.ofHours(8)));
                statement.setOpeningReceivable(new BigDecimal("1.0000"));
                statement.setClosingReceivable(new BigDecimal("2.0000"));
                statement.setReceivableIncrease(new BigDecimal("3.0000"));
                statement.setReceivableRed(new BigDecimal("0.0000"));
                statement.setWriteOffNet(new BigDecimal("0.0000"));
                statement.setReceiptNet(new BigDecimal("1.0000"));
                statement.setRefundNet(new BigDecimal("0.0000"));

                ScmCustomerStatementItemVO item = new ScmCustomerStatementItemVO();
                item.setLineNo(1);
                item.setFactType("RECEIPT");
                item.setFactId(100L);
                item.setEventAt(OffsetDateTime.of(2026, 10, 20, 9, 30, 0, 0, ZoneOffset.ofHours(8)));
                item.setDocumentNo("SK-001");
                item.setRelatedNo("SO-001");
                item.setReceivableDelta(new BigDecimal("0.0000"));
                item.setReceiptDelta(new BigDecimal("1.0000"));
                item.setWriteOffDelta(new BigDecimal("0.0000"));
                item.setRefundDelta(new BigDecimal("0.0000"));
                item.setReceivableBalance(new BigDecimal("2.0000"));
                statement.setItems(List.of(item));
                return statement;
            }
        }
    }

    @Nested
    class PrintW5PgIT extends ScmW5PgITBase {
        @Nested

        class ScmPrintBatchIT {
            private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

            @Autowired
            private ScmPrintBatchService scmPrintBatchService;

            private MockedStatic<StpUtil> permissions;

            @BeforeEach
            void grantPermissions() {
                permissions = mockStatic(StpUtil.class);
                permissions.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
                permissions.when(() -> StpUtil.checkPermission(anyString())).thenAnswer(invocation -> null);
            }

            @AfterEach
            void releasePermissions() {
                permissions.close();
            }

            @Test
            @DisplayName("#1 按配送日期批量预览/打印两个客户，同一批次键重试不重复生成记录")
            void batchPrintByDeliveryDate() {
                LocalDate businessDate = LocalDate.now(ZONE).plusDays(1);
                Long firstCustomer = newCustomer();
                Long secondCustomer = newCustomer();
                Long firstOrder = confirmedSalesOrder(firstCustomer, newOnShelfSku("PB1"), "3.0000", "3.0000");
                Long secondOrder = confirmedSalesOrder(secondCustomer, newOnShelfSku("PB2"), "2.0000", "2.0000");
                moveDeliveryDate(firstOrder, businessDate);
                moveDeliveryDate(secondOrder, businessDate);
                // 行备注要跟着商品走到纸面：不是整单备注，也不是打印模板里的空列
                jdbc.update("UPDATE xsy_sales_order_item SET remark = '去皮' WHERE order_id = ?", firstOrder);
                evictMybatisCache();

                ScmPrintBatchForm form = new ScmPrintBatchForm();
                form.setDocumentType("CUSTOMER_SORTING_SHEET");
                form.setBusinessDate(businessDate);
                form.setCustomerIds(List.of(firstCustomer, secondCustomer));

                ScmPrintBatchResultVO preview = scmPrintBatchService.preview(form);
                assertThat(preview.getTotal()).isEqualTo(2);
                assertThat(preview.getSucceeded()).isEqualTo(2);
                assertThat(preview.getFailed()).isZero();
                assertThat(preview.getRenders()).extracting(render -> render.getDocumentType())
                    .containsOnly("CUSTOMER_SORTING_SHEET");
                assertThat(preview.getRenders()).filteredOn(render -> firstOrder.equals(render.getBusinessId()))
                    .singleElement().satisfies(render -> assertThat(render.getRows()).singleElement()
                        .satisfies(row -> assertThat(row.get("remark")).isEqualTo("去皮")));
                assertThat(preview.getItems()).extracting(item -> item.getOrderId())
                    .containsExactlyInAnyOrder(firstOrder, secondOrder);

                form.setBatchKey(prefix + ":batch");
                ScmPrintBatchResultVO printed = scmPrintBatchService.print(form);
                assertThat(printed.getSucceeded()).isEqualTo(2);
                assertThat(printed.getItems()).extracting(item -> item.getRecordId()).doesNotContainNull();
                int records = printRecords(firstOrder) + printRecords(secondOrder);
                assertThat(records).isEqualTo(2);

                // 同一次点击的重试沿用同一个批量键，逐单命中重复请求返回原结果
                scmPrintBatchService.print(form);
                assertThat(printRecords(firstOrder) + printRecords(secondOrder)).isEqualTo(records);
            }

            @Test
            @DisplayName("#1b 送货单按已完成分拣结果打印：缺货称出 0 也印 0，不再回落到下单量")
            void deliveryNotePrintsSortedZeroInsteadOfOrderedQuantity() {
                Long customerId = newCustomer();
                Long orderId = confirmedSalesOrder(customerId, newOnShelfSku("PB0"), "3.0000", "3.0000");
                moveDeliveryDate(orderId, LocalDate.now(ZONE));
                Long taskId = jdbc.queryForObject("""
                        INSERT INTO xsy_sorting_task (task_no, warehouse_id, warehouse_name_snapshot, status, completed_at,
                            version, deleted, created_at, updated_at, created_by)
                        VALUES (?, ?, '测试仓', 'COMPLETED', CURRENT_TIMESTAMP, 0, FALSE, CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP, '1:1') RETURNING id
                        """, Long.class, "SRTIT" + UUID.randomUUID().toString().substring(0, 8), seedWarehouseId());
                jdbc.update("""
                        INSERT INTO xsy_sorting_task_item (task_id, sales_order_id, sales_order_item_id, order_no_snapshot,
                            customer_id, customer_name_snapshot, spu_id, sku_id, product_name_snapshot, sale_unit_snapshot,
                            product_type_snapshot, planned_quantity_snapshot, sorted_quantity, result, reason,
                            occupation_status, version, deleted, created_at, updated_at, created_by)
                        SELECT ?, oi.order_id, oi.id, o.order_no, o.customer_id, o.customer_name_snapshot, oi.spu_id,
                            oi.sku_id, oi.product_name_snapshot, oi.sale_unit_snapshot, oi.product_type_snapshot,
                            oi.ordered_quantity, 0, 'OUT_OF_STOCK', '缺货', 'ACTIVE', 0, FALSE, CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP, '1:1'
                        FROM xsy_sales_order_item oi JOIN xsy_sales_order o ON o.id = oi.order_id
                        WHERE oi.order_id = ?
                        """, taskId, orderId);

                ScmPrintBatchForm form = new ScmPrintBatchForm();
                form.setDocumentType("CUSTOMER_DELIVERY_NOTE");
                form.setBusinessDate(LocalDate.now(ZONE));
                form.setOrderIds(List.of(orderId));

                ScmPrintBatchResultVO preview = scmPrintBatchService.preview(form);

                assertThat(preview.getSucceeded()).isEqualTo(1);
                assertThat(preview.getRenders()).singleElement().satisfies(render -> {
                    assertThat(render.getRows()).singleElement().satisfies(row -> {
                        assertThat(row.get("actualQuantity")).isEqualTo("0.0000");
                        assertThat(row.get("settlementLineAmount")).isEqualTo("0.0000");
                    });
                    assertThat(render.getTotals()).filteredOn(field -> "settlementTotalAmount".equals(field.getKey()))
                        .singleElement().satisfies(field -> assertThat(field.getValue()).isEqualTo("0.0000"));
                });
            }

            @Test
            @DisplayName("#2 一张失败不影响其他单；不支持的单据类型整批拒绝（41313）")
            void oneFailureDoesNotRollBackTheBatch() {
                Long customerId = newCustomer();
                Long orderId = confirmedSalesOrder(customerId, newOnShelfSku("PB3"), "1.0000", "1.0000");

                ScmPrintBatchForm form = new ScmPrintBatchForm();
                form.setDocumentType("CUSTOMER_DELIVERY_NOTE");
                form.setBusinessDate(LocalDate.now(ZONE));
                form.setOrderIds(List.of(orderId, 987_654_321L));

                ScmPrintBatchResultVO result = scmPrintBatchService.preview(form);
                assertThat(result.getTotal()).isEqualTo(2);
                assertThat(result.getSucceeded()).isEqualTo(1);
                assertThat(result.getFailed()).isEqualTo(1);
                assertThat(result.getItems()).filteredOn(item -> !item.isSuccess())
                    .singleElement().satisfies(item -> {
                        assertThat(item.getOrderId()).isEqualTo(987_654_321L);
                        assertThat(item.getMessage()).isNotBlank();
                    });

                ScmPrintBatchForm unsupported = new ScmPrintBatchForm();
                unsupported.setDocumentType("PURCHASE_ORDER");
                unsupported.setBusinessDate(LocalDate.now(ZONE));
                expectCode(() -> scmPrintBatchService.preview(unsupported), 41313);
            }

            /** 批量取数按 expect_delivery_time 的北京时间自然日；夹具确认后回写目标配送时间。 */
            private void moveDeliveryDate(Long orderId, LocalDate businessDate) {
                OffsetDateTime deliveryAt = businessDate.atTime(10, 0).atOffset(ZoneOffset.ofHours(8));
                assertThat(jdbc.update("UPDATE xsy_sales_order SET expect_delivery_time = ? WHERE id = ?",
                    deliveryAt, orderId)).isEqualTo(1);
            }

            private int printRecords(Long orderId) {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_print_record WHERE business_id = ? "
                    + "AND document_type IN ('CUSTOMER_SORTING_SHEET', 'CUSTOMER_DELIVERY_NOTE')",
                    Integer.class, orderId);
            }
        }

        @Nested

        class ScmPrintCenterPgIT {
            private static final String PURCHASE_ORDER_TYPE = "PURCHASE_ORDER";

            @Autowired
            ScmPrintService printService;

            @Autowired
            ScmPrintTemplateService templateService;

            @Autowired
            ScmPrintCustomerBindingService bindingService;

            private MockedStatic<StpUtil> permissions;

            @BeforeEach
            void grantPermissions() {
                permissions = mockStatic(StpUtil.class);
                permissions.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
            }

            @AfterEach
            void releasePermissions() {
                permissions.close();
            }

            private Long newSubmittedPurchaseOrder() {
                Long supplierId = newSupplier("PRINT");
                Long skuId = newOnShelfSku("PRINT-PO");
                linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
                var order = createDraftOrder("PRINT-PO", supplierId, skuId, "10.0000", "5.0000");
                return submitOrder(order.getId()).getId();
            }

            /** 防重复请求编号带本用例前缀：类是共享库上的 PG IT，不能用全局唯一的固定键。 */
            private String key(String tag) {
                return "PRINT-" + tag;
            }

            private int recordCount(Long orderId) {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_print_record"
                                + " WHERE document_type = ? AND business_id = ?",
                        Integer.class, PURCHASE_ORDER_TYPE, orderId);
            }

            private Long latestRecordId(Long orderId) {
                return jdbc.queryForObject("SELECT max(id) FROM xsy_print_record"
                                + " WHERE document_type = ? AND business_id = ?",
                        Long.class, PURCHASE_ORDER_TYPE, orderId);
            }

            @Test
            @DisplayName("打印记录保存模板版本与版面：事后改版不回写历史重印")
            void printFreezesTemplateVersionAndLayoutAgainstLaterEdits() {
                Long orderId = newSubmittedPurchaseOrder();
                Long templateId = templateService.requireDefault(PURCHASE_ORDER_TYPE).getId();

                ScmPrintActionForm action = new ScmPrintActionForm();
                action.setTemplateId(templateId);

                ScmPrintRenderVO printed = printService.print(PURCHASE_ORDER_TYPE, orderId, action, key("print"));
                assertThat(printed.getTemplateVersion()).isNotNull();
                String frozenTitle = printed.getTitle();
                String frozenPaper = printed.getPaper();
                String frozenFooter = printed.getFooterNote();
                Integer frozenVersion = printed.getTemplateVersion();
                assertThat(frozenTitle).isNotBlank();

                // 改版：只改标题与页脚（不动纸张，避免掺入与断言无关的版面规则）
                ScmPrintTemplateForm update = new ScmPrintTemplateForm();
                var detail = templateService.detail(templateId);
                update.setId(detail.getId());
                update.setDocumentType(detail.getDocumentType());
                update.setTemplateCode(detail.getTemplateCode());
                update.setTemplateName(detail.getTemplateName());
                update.setDefaultFlag(detail.getDefaultFlag());
                update.setEnabledFlag(detail.getEnabledFlag());
                update.setRemark(detail.getRemark());
                update.setVersion(detail.getVersion());
                var model = detail.getModel();
                model.setTitle("采购单（已改版）");
                model.setFooterNote("改版后的页脚说明");
                update.setModel(model.toMap());
                templateService.update(update);

                ScmPrintRenderVO reprint = printService.reprint(latestRecordId(orderId));
                assertThat(reprint.getTitle()).as("重印必须用记录里保存的版面，不能被新模板污染")
                        .isEqualTo(frozenTitle).isNotEqualTo("采购单（已改版）");
                assertThat(reprint.getFooterNote()).as("页脚同样取历史资料")
                        .isEqualTo(frozenFooter).isNotEqualTo("改版后的页脚说明");
                assertThat(reprint.getPaper()).isEqualTo(frozenPaper);
                assertThat(reprint.getTemplateVersion()).isEqualTo(frozenVersion);
                assertThat(reprint.isFrozen()).isTrue();
                assertThat(reprint.getBusinessId()).isEqualTo(orderId);
            }

            @Test
            @DisplayName("打印与重印都不改变采购单状态")
            void printAndReprintNeverTouchBusinessState() {
                Long orderId = newSubmittedPurchaseOrder();
                var before = reloadOrder(orderId);

                ScmPrintActionForm action = new ScmPrintActionForm();
                action.setTemplateId(templateService.requireDefault(PURCHASE_ORDER_TYPE).getId());
                printService.print(PURCHASE_ORDER_TYPE, orderId, action, key("state"));
                var afterPrint = reloadOrder(orderId);
                printService.reprint(latestRecordId(orderId));
                var afterReprint = reloadOrder(orderId);

                assertThat(afterPrint.getStatus()).isEqualTo(before.getStatus());
                assertThat(afterReprint.getStatus()).isEqualTo(before.getStatus());
                assertThat(afterReprint.getVersion()).isEqualTo(before.getVersion());
            }

            @Test
            @DisplayName("重印复判当前查看权：只有打印记录查询权读不出单据内容")
            void reprintRechecksCurrentViewPermission() {
                Long orderId = newSubmittedPurchaseOrder();
                ScmPrintActionForm action = new ScmPrintActionForm();
                action.setTemplateId(templateService.requireDefault(PURCHASE_ORDER_TYPE).getId());
                printService.print(PURCHASE_ORDER_TYPE, orderId, action, key("reprint-perm"));
                Long recordId = latestRecordId(orderId);

                // 先前打印成功过，此刻撤回功能权限：旧记录绝不能成为读内容的后门。
                // 这里用 anyString：要守的性质是「重印必须重新做一次权限校验并把拒绝传出去」，
                // 而不是钉住某个具体权限码字符串（码值归 PurchasePermission 目录管）。
                permissions.when(() -> StpUtil.checkPermission(anyString()))
                        .thenThrow(new IllegalStateException("权限已撤回"));

                assertThatThrownBy(() -> printService.reprint(recordId))
                        .as("重印必须重新检查当前单据查看权")
                        .isInstanceOf(RuntimeException.class);
            }

            @Test
            @DisplayName("同一防重复请求编号重试只落一条打印记录")
            void retryWithSameIdempotencyKeyDoesNotDuplicateRecord() {
                Long orderId = newSubmittedPurchaseOrder();
                ScmPrintActionForm action = new ScmPrintActionForm();
                action.setTemplateId(templateService.requireDefault(PURCHASE_ORDER_TYPE).getId());

                String idempotencyKey = key("retry");
                printService.print(PURCHASE_ORDER_TYPE, orderId, action, idempotencyKey);
                printService.print(PURCHASE_ORDER_TYPE, orderId, action, idempotencyKey);

                assertThat(recordCount(orderId))
                        .as("重试不能重复生成不该重复的业务记录")
                        .isEqualTo(1);
            }

            /** 以默认模板为底本造一份合法表单：只改要改的字段，避免掺进与断言无关的版面规则。 */
            private ScmPrintTemplateForm templateForm(String code, String title) {
                var base = templateService.requireDefault(PURCHASE_ORDER_TYPE);
                var model = templateService.detail(base.getId()).getModel();
                model.setTitle(title);
                ScmPrintTemplateForm form = new ScmPrintTemplateForm();
                form.setDocumentType(PURCHASE_ORDER_TYPE);
                form.setTemplateCode(code);
                form.setTemplateName(code);
                form.setModel(model.toMap());
                return form;
            }

            private int defaultCount() {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_print_template"
                                + " WHERE document_type = ? AND default_flag = TRUE AND deleted = FALSE",
                        Integer.class, PURCHASE_ORDER_TYPE);
            }

            @Test
            @DisplayName("模板编码唯一：重复编码被拒")
            void createRejectsDuplicatedTemplateCode() {
                var existing = templateService.requireDefault(PURCHASE_ORDER_TYPE);
                ScmPrintTemplateForm duplicate = templateForm(existing.getTemplateCode(), "重复编码模板");

                expectCode(() -> templateService.create(duplicate), 41302);
            }

            @Test
            @DisplayName("非白名单字段拒绝：明细列只能用该单据类型登记的字段")
            void createRejectsFieldOutsideCatalog() {
                ScmPrintTemplateForm form = templateForm(key("field"), "越界字段模板");
                var model = templateService.detail(templateService.requireDefault(PURCHASE_ORDER_TYPE).getId()).getModel();
                model.getColumns().add("internalCostPrice");
                form.setModel(model.toMap());

                expectCode(() -> templateService.create(form), 41306);
            }

            @Test
            @DisplayName("同一单据类型只允许一个默认模板")
            void creatingNewDefaultDemotesTheOldOne() {
                assertThat(defaultCount()).as("种子数据里每类只有一个默认模板").isEqualTo(1);

                ScmPrintTemplateForm form = templateForm(key("default"), "新的采购单默认模板");
                form.setDefaultFlag(Boolean.TRUE);
                templateService.create(form);

                assertThat(defaultCount()).as("新的成为默认后，旧默认必须被降级，不能同时存在两个")
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("已被打印记录引用的模板不许删除")
            void deleteRejectsTemplateInUse() {
                Long orderId = newSubmittedPurchaseOrder();
                Long templateId = templateService.requireDefault(PURCHASE_ORDER_TYPE).getId();
                ScmPrintActionForm action = new ScmPrintActionForm();
                action.setTemplateId(templateId);
                printService.print(PURCHASE_ORDER_TYPE, orderId, action, key("in-use"));

                var template = templateService.detail(templateId);
                expectCode(() -> templateService.delete(templateId, template.getVersion()), 41303);
            }

            @Test
            @DisplayName("模板编辑受乐观锁保护：旧版本号改不动已被更新的模板")
            void updateRejectsStaleVersion() {
                var detail = templateService.detail(templateService.requireDefault(PURCHASE_ORDER_TYPE).getId());

                ScmPrintTemplateForm first = templateForm(detail.getTemplateCode(), "第一次改名");
                first.setId(detail.getId());
                first.setVersion(detail.getVersion());
                first.setDocumentType(detail.getDocumentType());
                first.setTemplateName("第一次改名");
                var model = detail.getModel();
                model.setTitle("第一次改名");
                first.setModel(model.toMap());
                templateService.update(first);

                // 再拿改动前的版本号提交：并发下后到的一方不能把前一个人的改动盖掉
                ScmPrintTemplateForm stale = first;
                stale.setVersion(detail.getVersion());
                var other = templateService.detail(detail.getId()).getModel();
                other.setTitle("第二次改名");
                stale.setModel(other.toMap());

                assertThatThrownBy(() -> templateService.update(stale))
                        .as("乐观锁必须拒掉过期版本")
                        .isInstanceOf(com.xsy.scm.common.exception.ScmBusinessException.class);
            }

            @Test
            @DisplayName("客户专属模板优先于默认模板，显式指定模板仍然优先")
            void customerBindingResolvesBeforeDefaultAndExplicitTemplateWins() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("PRINT-SO");
                Long orderId = confirmedSalesOrder(customerId, skuId, "5.0000", "5.0000");
                var defaultTemplate = templateService.requireDefault("SALES_ORDER");

                ScmPrintTemplateForm form = new ScmPrintTemplateForm();
                var model = templateService.detail(defaultTemplate.getId()).getModel();
                model.setTitle("客户专属销售单");
                form.setDocumentType("SALES_ORDER");
                form.setTemplateCode("BOUND_SO_" + orderId);
                form.setTemplateName("客户专属销售单模板");
                form.setModel(model.toMap());
                Long boundTemplateId = templateService.create(form);

                ScmPrintCustomerTemplateForm binding = new ScmPrintCustomerTemplateForm();
                binding.setCustomerId(customerId);
                binding.setDocumentType("SALES_ORDER");
                binding.setTemplateId(boundTemplateId);
                bindingService.bind(binding);

                ScmPrintRenderVO bound = printService.preview("SALES_ORDER", orderId, null);
                assertThat(bound.getTemplateId()).as("未指定模板时使用客户绑定模板").isEqualTo(boundTemplateId);
                assertThat(bound.getTitle()).isEqualTo("客户专属销售单");

                var options = printService.templateOptions("SALES_ORDER", orderId);
                assertThat(options.getFirst().getId()).isEqualTo(boundTemplateId);
                assertThat(options.getFirst().getCustomerBound()).isTrue();

                ScmPrintRenderVO explicit = printService.preview("SALES_ORDER", orderId, defaultTemplate.getId());
                assertThat(explicit.getTemplateId()).as("显式选择的模板优先于客户绑定").isEqualTo(defaultTemplate.getId());

                bindingService.unbind("SALES_ORDER", customerId);
                ScmPrintRenderVO fallback = printService.preview("SALES_ORDER", orderId, null);
                assertThat(fallback.getTemplateId()).as("解除绑定后回退到类型默认模板")
                        .isEqualTo(defaultTemplate.getId());
            }
        }
    }
}
