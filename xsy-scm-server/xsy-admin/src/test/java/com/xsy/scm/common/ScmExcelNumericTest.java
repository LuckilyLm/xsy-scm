package com.xsy.scm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xsy.scm.common.excel.ScmExcel;
import com.xsy.scm.common.exception.ScmBusinessException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Excel金额可直接求和，同时保留长编码与超精度历史数值。 */
class ScmExcelNumericTest {
    @Test
    void normalAmountsAreNumericAndCanBeSummed() throws Exception {
        var titles = List.of("单号", "金额");
        var response = new MockHttpServletResponse();
        ScmExcel.write(response, "金额.xlsx", "金额", titles, List.of(
            ScmExcel.row(titles, "123456789012345678", new BigDecimal("0.1000")),
            ScmExcel.row(titles, "123456789012345679", new BigDecimal("0.2000"))));
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("123456789012345678");
            assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            var sum = sheet.createRow(3).createCell(1);
            sum.setCellFormula("SUM(B2:B3)");
            book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(sum);
            assertThat(BigDecimal.valueOf(sum.getNumericCellValue()).setScale(4, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("0.3000");
            assertThatThrownBy(() -> ScmExcel.setNumber(sum, new BigDecimal("99999999999999.9999")))
                .isInstanceOf(ScmBusinessException.class).hasMessageContaining("15位有效数字");
        }
        assertThat(ScmExcel.row(List.of("金额"), new BigDecimal("99999999999999.9999")))
            .containsExactly("99999999999999.9999");
    }
}
