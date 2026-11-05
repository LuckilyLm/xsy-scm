package com.xsy.scm.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xsy.scm.report.domain.form.ScmCustomerMonthlyProfitQueryForm;
import com.xsy.scm.report.domain.vo.ScmCustomerMonthlyProfitVO;
import com.xsy.scm.report.service.ScmCustomerMonthlyExcelService;
import com.xsy.scm.report.service.ScmCustomerMonthlyProfitService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

/** 客户十二列对账单应具备期间、可复算公式和未知成本语义。 */
class ScmCustomerMonthlyExcelServiceTest {
    @Test
    void preservesCustomerHeadersAndReconcilesEveryDerivedAmount() throws Exception {
        var report = mock(ScmCustomerMonthlyProfitService.class);
        var query = new ScmCustomerMonthlyProfitQueryForm();
        query.setStartDate(LocalDate.of(2026, 11, 1));
        query.setEndDate(LocalDate.of(2026, 11, 30));
        var row = new ScmCustomerMonthlyProfitVO();
        row.setDimensionName("测试客户");
        row.setStatementAmount(new BigDecimal("1050"));
        row.setPaidAmount(new BigDecimal("300"));
        row.setPostingAmount(new BigDecimal("50"));
        row.setPostingTaxAmount(new BigDecimal("6.5"));
        row.setSalesCostAmount(new BigDecimal("600"));
        row.setFreightAmount(new BigDecimal("5"));
        row.setCommissionAmount(new BigDecimal("3"));
        when(report.export(query)).thenReturn(List.of(row));
        var service = new ScmCustomerMonthlyExcelService(report);
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(service.export(query)))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("对账日期");
            assertThat(sheet.getRow(3).getCell(2).getStringCellValue()).isEqualTo("2026-11-01 至 2026-11-30");
            assertThat(sheet.getRow(3).getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(3).getCell(5).getNumericCellValue()).isEqualTo(750);
            assertThat(sheet.getRow(3).getCell(8).getNumericCellValue()).isEqualTo(993.5);
            assertThat(sheet.getRow(3).getCell(12).getNumericCellValue()).isEqualTo(368.5);
            assertThat(sheet.getRow(4).getCell(12).getNumericCellValue()).isEqualTo(368.5);
            assertThat(sheet.isColumnHidden(13)).isTrue();
        }
        row.setSalesCostAmount(null);
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(service.export(query)))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(3).getCell(9).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(sheet.getRow(3).getCell(12).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(4).getCell(12).getCellType()).isEqualTo(CellType.BLANK);
        }
    }
}
