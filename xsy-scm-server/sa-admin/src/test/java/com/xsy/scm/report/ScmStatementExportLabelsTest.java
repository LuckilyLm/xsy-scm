package com.xsy.scm.report;

import com.xsy.scm.report.controller.ScmCustomerStatementController;
import com.xsy.scm.report.controller.ScmSupplierStatementController;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementVO;
import com.xsy.scm.report.domain.vo.ScmSupplierStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmSupplierStatementVO;
import com.xsy.scm.report.service.ScmCustomerStatementService;
import com.xsy.scm.report.service.ScmSupplierStatementService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScmStatementExportLabelsTest {
    @Test
    void customerWorkbookUsesPlainLabelsAndPreservesStoredAmounts() throws Exception {
        var service = mock(ScmCustomerStatementService.class);
        var version = new ScmCustomerStatementVO();
        version.setId(1L);
        version.setPartialScope(false);
        version.setItems(List.of("RECEIVABLE", "RED", "RECEIPT", "RECEIPT_REVERSE", "WRITE_OFF",
            "WRITE_OFF_REVERSE", "WAIVER", "WAIVER_REVERSE", "REFUND", "REFUND_REVERSE", "POSTING")
            .stream().map(type -> {
                var item = new ScmCustomerStatementItemVO();
                item.setFactType(type);
                if ("RED".equals(type)) item.setReceivableDelta(new BigDecimal("-12.3456"));
                return item;
            }).toList());
        when(service.detail(1L)).thenReturn(version);
        var response = new MockHttpServletResponse();
        new ScmCustomerStatementController(service).export(1L, response);
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheetAt(0);
            assertThat(columnValues(sheet, "类型")).containsExactly("销售应收", "退货扣减", "实际收款", "撤销收款记录",
                "账款抵扣", "撤销抵扣", "抹零", "撤销抹零", "实际退款", "撤销退款记录", "外部账单");
            assertThat(sheet.getRow(2).getCell(columnIndex(sheet, "应收变动")).getStringCellValue())
                .isEqualTo("-12.3456");
        }
    }

    @Test
    void supplierWorkbookUsesSupplierSpecificLabelsAndExplainsPartialCoverage() throws Exception {
        var service = mock(ScmSupplierStatementService.class);
        var version = new ScmSupplierStatementVO();
        version.setId(2L);
        version.setPartialScope(true);
        version.setItems(List.of("PAYABLE", "RED", "PAYMENT", "PAYMENT_REVERSE", "WRITE_OFF", "WRITE_OFF_REVERSE")
            .stream().map(type -> {
                var item = new ScmSupplierStatementItemVO();
                item.setFactType(type);
                return item;
            }).toList());
        when(service.detail(2L)).thenReturn(version);
        var response = new MockHttpServletResponse();
        new ScmSupplierStatementController(service).export(2L, response);
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheetAt(0);
            assertThat(columnValues(sheet, "类型")).containsExactly("采购应付", "应付扣减", "实际付款", "撤销付款记录",
                "付款抵扣", "撤销抵扣");
            assertThat(sheet.getRow(1).getCell(columnIndex(sheet, "统计范围")).getStringCellValue())
                .contains("本次筛选范围", "该供应商的全部付款", "不能直接核对");
        }
    }

    private List<String> columnValues(Sheet sheet, String title) {
        int column = columnIndex(sheet, title);
        var values = new ArrayList<String>();
        for (int row = 1; row <= sheet.getLastRowNum(); row++) {
            values.add(sheet.getRow(row).getCell(column).getStringCellValue());
        }
        return values;
    }

    private int columnIndex(Sheet sheet, String title) {
        for (var cell : sheet.getRow(0)) {
            if (title.equals(cell.getStringCellValue())) return cell.getColumnIndex();
        }
        throw new AssertionError("Missing export column: " + title);
    }
}
