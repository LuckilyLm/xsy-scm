package net.lab1024.sa.admin.module.scm.order;

import net.lab1024.sa.admin.module.scm.customer.dao.CustomerDao;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerEntity;
import net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderImportResultVO;
import net.lab1024.sa.admin.module.scm.order.service.SalesOrderImportService;
import net.lab1024.sa.admin.module.scm.order.service.SalesOrderService;
import net.lab1024.sa.admin.module.scm.pricing.constant.ScmPriceSourceEnum;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.ResolvedPriceVO;
import net.lab1024.sa.admin.module.scm.pricing.service.PriceResolver;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionVO;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesOrderImportServiceTest {
    private final CustomerDao customers = mock(CustomerDao.class);
    private final ProductSkuOptionDao skus = mock(ProductSkuOptionDao.class);
    private final SalesOrderService orders = mock(SalesOrderService.class);
    private final PriceResolver prices = mock(PriceResolver.class);
    private final SalesOrderImportService service = new SalesOrderImportService(customers, skus, orders, prices);
    private ResolvedPriceVO price;

    @BeforeEach
    void catalog() {
        var customer = new CustomerEntity();
        customer.setId(1L);
        customer.setCustomerCode("C-1");
        customer.setStatus("COOPERATING");
        when(customers.selectActiveByCodes(anyList())).thenReturn(List.of(customer));
        var sku = new ProductSkuOptionVO();
        sku.setSkuId(2L);
        sku.setSkuCode("S-1");
        when(skus.selectByCodes(anyList())).thenReturn(List.of(sku));
        price = new ResolvedPriceVO();
        price.setSkuId(2L);
        price.setSellable(true);
        price.price(new BigDecimal("1.2000"), ScmPriceSourceEnum.MARKET, null);
        when(prices.resolve(eq(1L), anyList(), any())).thenReturn(List.of(price));
    }

    private MockMultipartFile workbook(Consumer<XSSFWorkbook> edit) throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("销售订单");
            String[] headers = {"模板版本", "导入订单标识", "客户编码", "收货人", "联系电话", "收货地址", "期望配送时间", "SKU编码", "下单数量", "人工单价", "改价原因", "订单备注"};
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            // A physical blank row must not shift the diagnostic for Excel row 3.
            sheet.createRow(1);
            var row = sheet.createRow(2);
            String[] data = {"1.0", "ORDER-A", "C-1", "张三", "13800000000", "测试地址", "", "S-1", "2.0000", "", "", ""};
            for (int i = 0; i < data.length; i++) row.createCell(i).setCellValue(data[i]);
            edit.accept(book);
            book.write(bytes);
            return new MockMultipartFile("file", "orders.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        }
    }

    @Test
    void reportsActualRowAndFieldAfterBlankRow() throws Exception {
        var file = workbook(book -> book.getSheetAt(0).getRow(2).getCell(8).setCellValue("0"));
        var result = service.importFile(file, "key", false);
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isEqualTo(3);
            assertThat(error.getColumn()).isEqualTo("下单数量");
            assertThat(error.getCode()).isEqualTo("DECIMAL_INVALID");
        });
        verifyNoInteractions(orders);
    }

    @Test
    void validatesHeadersEvenForOtherwiseValidData() throws Exception {
        var result = service.importFile(workbook(book -> book.getSheetAt(0).getRow(0).getCell(7).setCellValue("错误列")), "key", false);
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isOne();
            assertThat(error.getColumn()).isEqualTo("H");
            assertThat(error.getCode()).isEqualTo("HEADER_INVALID");
        });
        verifyNoInteractions(orders);
    }

    @Test
    void formulaHasCellDiagnostic() throws Exception {
        var result = service.importFile(workbook(book -> book.getSheetAt(0).getRow(2).getCell(8).setCellFormula("1+1")), "key", false);
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isEqualTo(3);
            assertThat(error.getColumn()).isEqualTo("下单数量");
            assertThat(error.getCode()).isEqualTo("CELL_INVALID");
        });
        verifyNoInteractions(orders);
    }

    @Test
    void malformedWorkbookReturnsFileError() throws Exception {
        var file = new MockMultipartFile("file", "broken.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        assertThat(service.importFile(file, "key", false).getErrors()).extracting("code").contains("FILE_INVALID");
        verifyNoInteractions(orders);
    }

    @Test
    void doesNotSilentlyIgnoreAdditionalSheets() throws Exception {
        var result = service.importFile(workbook(book -> book.createSheet("更多订单")), "key", false);
        assertThat(result.getErrors()).extracting("code").contains("SHEET_COUNT");
        verifyNoInteractions(orders);
    }

    @Test
    void unpricedAndUnavailableRowsAreLocatedBeforeWriting() throws Exception {
        price.price(null, ScmPriceSourceEnum.MARKET, null);
        var file = workbook(book -> {
        });
        var result = service.importFile(file, "key", false);
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isEqualTo(3);
            assertThat(error.getColumn()).isEqualTo("人工单价");
            assertThat(error.getCode()).isEqualTo("UNPRICED");
        });
        price.setSellable(false);
        assertThat(service.importFile(file, "key", false).getErrors()).extracting("code").contains("SKU_NOT_SELLABLE");
        verifyNoInteractions(orders);
    }

    @Test
    void manualPriceRequiresPermissionAndReason() throws Exception {
        var result = service.importFile(workbook(book -> book.getSheetAt(0).getRow(2).getCell(9).setCellValue("0")), "key", false);
        assertThat(result.getErrors()).extracting("code").contains("PRICE_OVERRIDE_FORBIDDEN", "OVERRIDE_REASON_REQUIRED");
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e99999999", "0.00001", "-2", "999999999999999"})
    void rejectsInvalidNumbersWithoutWriting(String quantity) throws Exception {
        var result = service.importFile(workbook(book -> book.getSheetAt(0).getRow(2).getCell(8).setCellValue(quantity)), "key", false);
        assertThat(result.getErrors()).extracting("code").contains("DECIMAL_INVALID");
        verifyNoInteractions(orders);
    }

    @Test
    void detectsOverflowBeforeDatabaseWrites() throws Exception {
        price.price(new BigDecimal("99999999999999.0000"), ScmPriceSourceEnum.MARKET, null);
        assertThat(service.importFile(workbook(book -> {
        }), "key", false).getErrors()).extracting("code").contains("AMOUNT_OVERFLOW");
        verifyNoInteractions(orders);
    }

    @Test
    void numericDisplayFormatDoesNotHideInvalidPrecision() throws Exception {
        var result = service.importFile(workbook(book -> {
            var cell = book.getSheetAt(0).getRow(2).getCell(8);
            cell.setCellValue(1.23456);
            var style = book.createCellStyle();
            style.setDataFormat(book.createDataFormat().getFormat("0.00"));
            cell.setCellStyle(style);
        }), "key", false);
        assertThat(result.getErrors()).extracting("code").contains("DECIMAL_INVALID");
        verifyNoInteractions(orders);
    }

    @Test
    void zeroPriceIsValidAndNumericCellsBecomeFixedPointStrings() throws Exception {
        price.price(BigDecimal.ZERO, ScmPriceSourceEnum.MARKET, null);
        when(orders.importOrders(anyList(), anyString(), eq("key"), eq(1))).thenAnswer(invocation -> {
            List<SalesOrderAddForm> forms = invocation.getArgument(0);
            assertThat(forms.getFirst().getItems().getFirst().getOrderedQuantity()).isEqualTo("2.0000");
            assertThat(forms.getFirst().getOrderSource()).isEqualTo("IMPORT");
            return new SalesOrderImportResultVO();
        });
        assertThat(service.importFile(workbook(book -> book.getSheetAt(0).getRow(2).getCell(8).setCellValue(2)), "key", false).getTotalErrors()).isZero();
        verify(orders).importOrders(anyList(), anyString(), eq("key"), eq(1));
    }

    @Test
    void transactionFailureIsReturnedWithOrderAndRowContext() throws Exception {
        when(orders.importOrders(anyList(), anyString(), anyString(), anyInt())).thenThrow(
                new SalesOrderService.ImportOrderException(0, new ScmBusinessException(OrderErrorCode.ORDER_ITEM_NOT_FOUND)));
        var result = service.importFile(workbook(book -> {
        }), "key", false);
        assertThat(result.getOrders()).isEmpty();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getRowNumber()).isEqualTo(3);
            assertThat(error.getOrderKey()).isEqualTo("ORDER-A");
            assertThat(error.getMessage()).contains("整批已回滚");
        });
    }
}
