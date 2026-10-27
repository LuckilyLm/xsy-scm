package com.xsy.scm.order;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.order.constant.OrderErrorCode;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.vo.SalesOrderImportResultVO;
import com.xsy.scm.order.service.SalesOrderImportService;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 送货单版式导入：一张送货单一张订单，实数量即下单数量，导入单价需改价权限并自动留痕。
 */
class SalesOrderImportServiceTest {
    private static final String CUSTOMER_NAME = "西安测试客户";

    private final CustomerDao customers = mock(CustomerDao.class);
    private final ProductSkuOptionDao skus = mock(ProductSkuOptionDao.class);
    private final SalesOrderService orders = mock(SalesOrderService.class);
    private final PriceResolver prices = mock(PriceResolver.class);
    private final SalesOrderImportService service = new SalesOrderImportService(customers, skus, orders, prices);

    @BeforeEach
    void catalog() {
        when(customers.selectActiveByNames(anyList())).thenReturn(List.of(customer(1L, CUSTOMER_NAME)));
        when(skus.selectByProductNames(anyList())).thenReturn(List.of(sku(2L, "商品甲", "500g", "斤")));
        var price = new ResolvedPriceVO();
        price.setSkuId(2L);
        price.setSellable(true);
        price.price(new BigDecimal("1.2000"), ScmPriceSourceEnum.MARKET, null);
        when(prices.resolve(eq(1L), anyList(), any())).thenReturn(List.of(price));
        when(orders.importOrders(anyList(), anyString(), any(), anyInt())).thenReturn(written());
    }

    private SalesOrderImportResultVO written() {
        var result = new SalesOrderImportResultVO();
        result.setTotalRows(1);
        result.setTotalOrders(1);
        return result;
    }

    private CustomerEntity customer(Long id, String name) {
        var customer = new CustomerEntity();
        customer.setId(id);
        customer.setName(name);
        customer.setStatus("COOPERATING");
        customer.setContactName("张三");
        customer.setContactPhone("13800000000");
        customer.setAddress("测试地址");
        return customer;
    }

    private ProductSkuOptionVO sku(Long id, String productName, String specName, String saleUnit) {
        var sku = new ProductSkuOptionVO();
        sku.setSkuId(id);
        sku.setSkuCode("S-" + id);
        sku.setProductName(productName);
        sku.setSpecName(specName);
        sku.setSaleUnit(saleUnit);
        sku.setStatus("ON_SHELF");
        sku.setSpuStatus("ON_SHELF");
        sku.setCategoryStatus("ENABLED");
        sku.setMarketPrice(new BigDecimal("1.2000"));
        return sku;
    }

    /** 生成一张最小可用的送货单：第 3 行收货单位与日期、第 4 行表头、第 5 行一条明细、合计与签字栏。 */
    private MockMultipartFile workbook(Consumer<XSSFWorkbook> edit) throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("送货单");
            sheet.createRow(0).createCell(0).setCellValue("鲜蔬源(西安)供应链服务有限公司");
            sheet.createRow(1).createCell(0).setCellValue("送货单");
            var meta = sheet.createRow(2);
            meta.createCell(0).setCellValue("收货单位：");
            meta.createCell(1).setCellValue(CUSTOMER_NAME);
            meta.createCell(6).setCellValue("日期：");
            meta.createCell(7).setCellValue("2026-10-27");
            var header = sheet.createRow(3);
            String[] headers = {"序号", "商品名称", "单位", "规格", "实数量", "销售单价", "销售金额", "备注"};
            for (int column = 0; column < headers.length; column++)
                header.createCell(column).setCellValue(headers[column]);
            var line = sheet.createRow(4);
            line.createCell(0).setCellValue("1");
            line.createCell(1).setCellValue("商品甲");
            line.createCell(2).setCellValue("斤");
            line.createCell(3).setCellValue("500g");
            line.createCell(4).setCellValue(2);
            line.createCell(5).setCellValue(1.2);
            line.createCell(6).setCellValue(2.4);
            line.createCell(7).setCellValue("");
            sheet.createRow(5).createCell(0).setCellValue("合计金额");
            var footer = sheet.createRow(6);
            footer.createCell(1).setCellValue("销售方：");
            footer.createCell(7).setCellValue("收货方：");
            edit.accept(book);
            book.write(bytes);
            return new MockMultipartFile("file", "delivery-note.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        }
    }

    private List<SalesOrderAddForm> capturedForms() {
        @SuppressWarnings("unchecked")
        var captor = ArgumentCaptor.forClass(List.class);
        verify(orders).importOrders(captor.capture(), anyString(), any(), anyInt());
        @SuppressWarnings("unchecked")
        var forms = (List<SalesOrderAddForm>) captor.getValue();
        return forms;
    }

    @Test
    void importsOneOrderWithProfileAddressAndImportPrice() throws Exception {
        var result = service.importFile(workbook(book -> {
        }), "key", true);

        assertThat(result.getTotalErrors()).isZero();
        assertThat(result.getTotalOrders()).isOne();
        verify(orders).importOrders(anyList(), anyString(), eq("key"), eq(1));
        var form = capturedForms().getFirst();
        assertThat(form.getCustomerId()).isEqualTo(1L);
        assertThat(form.getOrderSource()).isEqualTo("IMPORT");
        assertThat(form.getExpectDeliveryTime()).isEqualTo(OffsetDateTime.parse("2026-10-27T00:00:00+08:00"));
        assertThat(form.getAddress().getReceiverName()).isEqualTo("张三");
        assertThat(form.getAddress().getReceiverPhone()).isEqualTo("13800000000");
        assertThat(form.getAddress().getAddress()).isEqualTo("测试地址");
        var item = form.getItems().getFirst();
        assertThat(item.getSkuId()).isEqualTo(2L);
        assertThat(item.getOrderedQuantity()).isEqualTo("2.0000");
        assertThat(item.getManualPriceOverride()).isTrue();
        assertThat(item.getUnitPrice()).isEqualTo("1.2000");
        assertThat(item.getOverrideReason()).isEqualTo("送货单导入");
    }

    @Test
    void customerNameMustMatchAnActiveCustomer() throws Exception {
        when(customers.selectActiveByNames(anyList())).thenReturn(List.of());
        var result = service.importFile(workbook(book -> {
        }), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("CUSTOMER_NOT_FOUND");
        verifyNoInteractions(orders);
    }

    @Test
    void duplicateCustomerNamesAreRejected() throws Exception {
        when(customers.selectActiveByNames(anyList())).thenReturn(
            List.of(customer(1L, CUSTOMER_NAME), customer(9L, CUSTOMER_NAME)));
        var result = service.importFile(workbook(book -> {
        }), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("CUSTOMER_AMBIGUOUS");
        verifyNoInteractions(orders);
    }

    @Test
    void specAndUnitMustMatchTheSku() throws Exception {
        var wrongSpec = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).getCell(3).setCellValue("1kg")), "key", true);
        assertThat(wrongSpec.getErrors()).extracting("code").contains("SKU_NOT_FOUND");

        var wrongUnit = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).getCell(2).setCellValue("kg")), "key", true);
        assertThat(wrongUnit.getErrors()).extracting("code").contains("UNIT_MISMATCH");
        verifyNoInteractions(orders);
    }

    @Test
    void blankSpecRequiresAUniqueSku() throws Exception {
        when(skus.selectByProductNames(anyList())).thenReturn(
            List.of(sku(2L, "商品甲", "500g", "斤"), sku(3L, "商品甲", "1kg", "斤")));
        var ambiguous = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).getCell(3).setCellValue("")), "key", true);
        assertThat(ambiguous.getErrors()).extracting("code").contains("SKU_AMBIGUOUS");

        when(skus.selectByProductNames(anyList())).thenReturn(List.of(sku(2L, "商品甲", "500g", "斤")));
        var unique = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).getCell(3).setCellValue("")), "key", true);
        assertThat(unique.getTotalErrors()).isZero();
    }

    @Test
    void amountMustEqualQuantityTimesPrice() throws Exception {
        var result = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).getCell(6).setCellValue("3.00")), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("AMOUNT_NOT_MATCH");
        verifyNoInteractions(orders);
    }

    @Test
    void importPriceRequiresOverridePermission() throws Exception {
        var result = service.importFile(workbook(book -> {
        }), "key", false);
        assertThat(result.getErrors()).extracting("code").contains("PRICE_OVERRIDE_FORBIDDEN");
        verifyNoInteractions(orders);
    }

    @Test
    void blankPriceFallsBackToSystemPrice() throws Exception {
        var result = service.importFile(workbook(book -> {
            var line = book.getSheetAt(0).getRow(4);
            line.getCell(5).setCellValue("");
            line.getCell(6).setCellValue("");
        }), "key", false);
        assertThat(result.getTotalErrors()).isZero();
        var item = capturedForms().getFirst().getItems().getFirst();
        assertThat(item.getManualPriceOverride()).isFalse();
        assertThat(item.getUnitPrice()).isNull();
        assertThat(item.getOverrideReason()).isNull();
    }

    @Test
    void unpricedRowWithoutManualPriceIsRejected() throws Exception {
        var price = new ResolvedPriceVO();
        price.setSkuId(2L);
        price.setSellable(true);
        price.price(null, null, null);
        when(prices.resolve(eq(1L), anyList(), any())).thenReturn(List.of(price));
        var result = service.importFile(workbook(book -> {
            var line = book.getSheetAt(0).getRow(4);
            line.getCell(5).setCellValue("");
            line.getCell(6).setCellValue("");
        }), "key", false);
        assertThat(result.getErrors()).extracting("code").contains("UNPRICED");
        verifyNoInteractions(orders);
    }

    @Test
    void missingCustomerAddressBlocksImport() throws Exception {
        var customer = customer(1L, CUSTOMER_NAME);
        customer.setAddress(null);
        when(customers.selectActiveByNames(anyList())).thenReturn(List.of(customer));
        var result = service.importFile(workbook(book -> {
        }), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("CUSTOMER_PROFILE_INCOMPLETE");
        verifyNoInteractions(orders);
    }

    @Test
    void formulaAmountIsReadFromItsCachedValue() throws Exception {
        var result = service.importFile(workbook(book -> {
            book.getSheetAt(0).getRow(4).getCell(6).setCellFormula("E5*F5");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
        }), "key", true);
        assertThat(result.getTotalErrors()).isZero();
        verify(orders).importOrders(anyList(), anyString(), eq("key"), eq(1));
    }

    @Test
    void formulaAmountWithTextResultIsRejected() throws Exception {
        var result = service.importFile(workbook(book -> {
            book.getSheetAt(0).getRow(4).getCell(6).setCellFormula("B5");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
        }), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("CELL_INVALID");
        verifyNoInteractions(orders);
    }

    @Test
    void numericDisplayFormatDoesNotHideInvalidPrecision() throws Exception {
        var result = service.importFile(workbook(book -> {
            var cell = book.getSheetAt(0).getRow(4).getCell(4);
            cell.setCellValue(1.23456);
            var style = book.createCellStyle();
            style.setDataFormat(book.createDataFormat().getFormat("0.00"));
            cell.setCellStyle(style);
        }), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("DECIMAL_INVALID");
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e99999999", "0", "-2", "999999999999999"})
    void rejectsInvalidQuantityWithoutWriting(String quantity) throws Exception {
        var result = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).getCell(4).setCellValue(quantity)), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("DECIMAL_INVALID");
        verifyNoInteractions(orders);
    }

    @Test
    void formulaInQuantityIsRejected() throws Exception {
        var result = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).getCell(4).setCellFormula("1+1")), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("CELL_INVALID");
        verifyNoInteractions(orders);
    }

    @Test
    void malformedWorkbookReturnsFileError() throws Exception {
        var file = new MockMultipartFile("file", "broken.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        assertThat(service.importFile(file, "key", false).getErrors()).extracting("code").contains("FILE_INVALID");
        verifyNoInteractions(orders);
    }

    @Test
    void additionalSheetsAreNotSilentlyIgnored() throws Exception {
        var result = service.importFile(workbook(book -> book.createSheet("更多送货单")), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("SHEET_COUNT");
        verifyNoInteractions(orders);
    }

    @Test
    void missingHeaderIsReported() throws Exception {
        var result = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(3).getCell(4).setCellValue("数量")), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("HEADER_INVALID");
        verifyNoInteractions(orders);
    }

    @Test
    void transactionFailureIsReturnedWithRowContext() throws Exception {
        when(orders.importOrders(anyList(), anyString(), any(), anyInt())).thenThrow(
            new SalesOrderService.ImportOrderException(0, new ScmBusinessException(OrderErrorCode.ORDER_ITEM_NOT_FOUND)));
        var result = service.importFile(workbook(book -> {
        }), "key", true);
        assertThat(result.getOrders()).isEmpty();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getRowNumber()).isEqualTo(5);
            assertThat(error.getOrderKey()).isEqualTo(CUSTOMER_NAME);
            assertThat(error.getMessage()).contains("整批已回滚");
        });
    }

    @Test
    void dateMustBeReadable() throws Exception {
        var result = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(2).getCell(7).setCellValue("不是日期")), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("DATE_INVALID");
        verifyNoInteractions(orders);
    }

    @Test
    void dateCellWithDateFormatIsAccepted() throws Exception {
        var result = service.importFile(workbook(book -> {
            var cell = book.getSheetAt(0).getRow(2).getCell(7);
            cell.setCellValue(LocalDate.of(2026, 10, 28));
            var style = book.createCellStyle();
            style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
            cell.setCellStyle(style);
        }), "key", true);
        assertThat(result.getTotalErrors()).isZero();
        assertThat(capturedForms().getFirst().getExpectDeliveryTime())
            .isEqualTo(OffsetDateTime.parse("2026-10-28T00:00:00+08:00"));
    }

    @Test
    void formulaDateIsRejected() throws Exception {
        var result = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(2).getCell(7).setCellFormula("\"2026-10-27\"")), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("CELL_INVALID");
    }

    @Test
    void rowRemarksAreAggregatedIntoOrderRemark() throws Exception {
        var result = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).getCell(7).setCellValue("早上送达")), "key", true);
        assertThat(result.getTotalErrors()).isZero();
        assertThat(capturedForms().getFirst().getRemark()).isEqualTo("早上送达");
    }

    @Test
    void amountWithoutPriceIsRejected() throws Exception {
        var result = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).getCell(5).setCellValue("")), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("AMOUNT_WITHOUT_PRICE");
        verifyNoInteractions(orders);
    }

    @Test
    void extraColumnsAreRejected() throws Exception {
        var result = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).createCell(8).setCellValue("额外")), "key", true);
        assertThat(result.getErrors()).extracting("code").contains("COLUMN_UNEXPECTED");
        verifyNoInteractions(orders);
    }

    @Test
    void zeroPriceIsAcceptedAsManualOverride() throws Exception {
        var result = service.importFile(workbook(book -> {
            var line = book.getSheetAt(0).getRow(4);
            line.getCell(5).setCellValue(0);
            line.getCell(6).setCellValue(0);
        }), "key", true);
        assertThat(result.getTotalErrors()).isZero();
        var item = capturedForms().getFirst().getItems().getFirst();
        assertThat(item.getManualPriceOverride()).isTrue();
        assertThat(item.getUnitPrice()).isEqualTo("0.0000");
    }

    @Test
    void missingUnitIsReportedOnce() throws Exception {
        var result = service.importFile(
            workbook(book -> book.getSheetAt(0).getRow(4).getCell(2).setCellValue("")), "key", true);
        assertThat(result.getErrors()).filteredOn(error -> "单位".equals(error.getColumn()))
            .extracting("code").containsExactly("REQUIRED");
        verifyNoInteractions(orders);
    }
}
