package com.xsy.scm.report.service;

import com.xsy.scm.common.excel.ScmExcel;
import com.xsy.scm.report.domain.form.ScmCustomerMonthlyProfitQueryForm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** 客户原版十二列月度对账导出；只消费已授权月报，不修改财务记录。 */
@Service
@RequiredArgsConstructor
public class ScmCustomerMonthlyExcelService {
    private final ScmCustomerMonthlyProfitService profitService;

    /**
     * 生成客户对账模板文件，派生金额使用可重算公式；成本缺失时成本、利润留空。
     *
     * @param form
     *            必填完整自然月查询，沿用月利润的权限、范围与导出上限
     * @return 完整xlsx字节；读取模板异常抛出IOException，授权或数值错误沿用业务异常
     */
    public byte[] export(ScmCustomerMonthlyProfitQueryForm form) throws IOException {
        var rows = profitService.export(form);
        try (var input = new ClassPathResource("template/customer-monthly-statement.xlsx").getInputStream();
            var book = new XSSFWorkbook(input);
            var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            book.setSheetName(0, "客户月度对账");
            CellStyle money = book.createCellStyle();
            money.cloneStyleFrom(sheet.getRow(3).getCell(3).getCellStyle());
            money.setDataFormat(book.createDataFormat().getFormat("0.0000"));
            // 保持客户可见的十二列；辅助记录隐藏但留在工作簿中，确保实际货款和利润可复算。
            String[] helpers = {"外部账单税金", "运费", "扣点金额", "专票税率", "服务费率"};
            for (int i = 0; i < helpers.length; i++) {
                sheet.getRow(0).createCell(13 + i).setCellValue(helpers[i]);
                sheet.setColumnHidden(13 + i, true);
            }
            sheet.getRow(1).createCell(16).setCellValue(0.01);
            sheet.getRow(1).createCell(17).setCellValue(0.06);
            boolean complete = true;
            String period = form.getStartDate() + " 至 " + form.getEndDate();
            for (int i = 0; i < rows.size(); i++) {
                var source = rows.get(i);
                Row row = sheet.getRow(i + 3);
                if (row == null)
                    row = sheet.createRow(i + 3);
                for (int col = 1; col < 16; col++) {
                    if (row.getCell(col) == null)
                        row.createCell(col);
                    if (col < 13)
                        row.getCell(col).setCellStyle(sheet.getRow(3).getCell(col).getCellStyle());
                    if (col >= 3)
                        row.getCell(col).setCellStyle(money);
                }
                row.getCell(1).setCellValue(source.getDimensionName());
                row.getCell(2).setCellValue(period);
                ScmExcel.setNumber(row.getCell(3), source.getStatementAmount());
                ScmExcel.setNumber(row.getCell(4), source.getPaidAmount());
                ScmExcel.setNumber(row.getCell(7), source.getPostingAmount());
                ScmExcel.setNumber(row.getCell(9), source.getSalesCostAmount());
                ScmExcel.setNumber(row.getCell(13), source.getPostingTaxAmount());
                ScmExcel.setNumber(row.getCell(14), source.getFreightAmount());
                ScmExcel.setNumber(row.getCell(15), source.getCommissionAmount());
                int n = i + 4;
                row.getCell(5).setCellFormula("ROUND(D" + n + "-E" + n + ",4)");
                // 开票金额沿用已声明的占位规则，不冒充新建的发票台账。
                row.getCell(6).setCellFormula("D" + n);
                row.getCell(8).setCellFormula("ROUND(D" + n + "-H" + n + "-N" + n + ",4)");
                row.getCell(10).setCellFormula("ROUND(D" + n + "*$Q$2,4)");
                row.getCell(11).setCellFormula("ROUND(D" + n + "*$R$2,4)");
                row.getCell(12).setCellFormula("IF(ISNUMBER(J" + n + "),ROUND(D" + n + "-J" + n + "-K" + n + "-L" + n
                    + "-O" + n + "-P" + n + ",4),\"\")");
                complete &= source.getSalesCostAmount() != null;
            }
            Row total = sheet.getRow(rows.size() + 3);
            if (total == null)
                total = sheet.createRow(rows.size() + 3);
            total.createCell(1).setCellValue("合计");
            total.createCell(2).setCellValue(period);
            for (int col = 3; col <= 12; col++) {
                var cell = total.createCell(col);
                cell.setCellStyle(money);
                if (!complete && (col == 9 || col == 12))
                    continue;
                String letter = org.apache.poi.ss.util.CellReference.convertNumToColString(col);
                if (rows.isEmpty())
                    ScmExcel.setNumber(cell, BigDecimal.ZERO);
                else
                    cell.setCellFormula("ROUND(SUM(" + letter + "4:" + letter + (rows.size() + 3) + "),4)");
            }
            Row note = sheet.createRow(rows.size() + 5);
            note.createCell(1).setCellValue("本表按所属期间发生额统计，不含期初欠款；开票金额暂按对账金额展示。成本未登记时利润留空。");
            sheet.addMergedRegion(new CellRangeAddress(rows.size() + 5, rows.size() + 5, 1, 12));
            sheet.setColumnWidth(2, 34 * 256);
            sheet.setRepeatingRows(CellRangeAddress.valueOf("1:3"));
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            book.setPrintArea(0, 0, 12, 0, rows.size() + 5);
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            book.write(output);
            return output.toByteArray();
        }
    }
}
