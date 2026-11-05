package com.xsy.scm.print.service;

import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.excel.ScmExcel;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.service.SalesOrderQueryService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** 客户单据复用发布模板与正式订单事实，采购价格不参与对客单据。 */
@Service
@RequiredArgsConstructor
public class CustomerSheetExcelService {
    private final SalesOrderQueryService orderQueryService;

    /**
     * 导出一张客户订单或送货单，不产生打印、库存或财务事实。
     *
     * @param orderId
     *            必填订单编号，沿用订单详情的数据范围校验
     * @param delivery
     *            true 取正式实发量，false 取下单量；未称重的实发量留空
     * @return 已完整生成的 xlsx；无权或订单不存在沿用订单错误，模板异常抛出 IOException
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] export(Long orderId, boolean delivery) throws IOException {
        SalesOrderDetailVO order = orderQueryService.detail(orderId);
        var items = order.getItems();
        if (items == null || items.isEmpty() || items.size() > 500) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        Map<Long, BigDecimal> sorted = delivery ? orderQueryService.sortedQuantities(orderId) : Map.of();
        Map<Long, BigDecimal> discounts = new HashMap<>();
        if (order.getDiscount() != null && order.getDiscount().getAllocations() != null) {
            order.getDiscount().getAllocations().forEach(a -> discounts.put(a.getOrderItemId(), a.getDiscountAmount()));
        }
        try (var input = new ClassPathResource("template/sales-order-import.xlsx").getInputStream();
            var book = new XSSFWorkbook(input);
            var output = new ByteArrayOutputStream()) {
            Sheet sheet = book.getSheetAt(0);
            book.setSheetName(0, delivery ? "送货单" : "订货单");
            sheet.getRow(1).getCell(0).setCellValue(delivery ? "送货单" : "订货单");
            sheet.getRow(2).createCell(1).setCellValue(order.getCustomerNameSnapshot());
            sheet.getRow(2).createCell(7)
                .setCellValue(order.getExpectDeliveryTime() == null
                    ? ""
                    : order.getExpectDeliveryTime().atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai"))
                        .toLocalDate().toString());
            // 客户原表有62个明细行；扩展时连同合计与签字栏一起移动，不能截断长订单。
            int extra = Math.max(0, items.size() - 62);
            if (extra > 0)
                sheet.shiftRows(66, 67, extra, true, false);
            CellStyle quantityStyle = book.createCellStyle();
            quantityStyle.cloneStyleFrom(sheet.getRow(4).getCell(4).getCellStyle());
            quantityStyle.setDataFormat(book.createDataFormat().getFormat("0.####"));
            CellStyle moneyStyle = book.createCellStyle();
            moneyStyle.cloneStyleFrom(sheet.getRow(4).getCell(6).getCellStyle());
            moneyStyle.setDataFormat(book.createDataFormat().getFormat("0.00"));
            boolean complete = true;
            for (int index = 0; index < items.size(); index++) {
                var item = items.get(index);
                var row = sheet.getRow(index + 4);
                if (row == null)
                    row = sheet.createRow(index + 4);
                for (int col = 0; col < 8; col++) {
                    if (row.getCell(col) == null)
                        row.createCell(col);
                    if (index >= 62)
                        row.getCell(col).setCellStyle(sheet.getRow(4).getCell(col).getCellStyle());
                }
                row.getCell(0).setCellValue(index + 1);
                row.getCell(1).setCellValue(item.getProductNameSnapshot());
                row.getCell(2).setCellValue(item.getSaleUnitSnapshot());
                row.getCell(3).setCellValue(item.getSpecNameSnapshot());
                BigDecimal quantity = delivery
                    ? sorted.getOrDefault(item.getItemId(), item.getActualQuantity())
                    : item.getOrderedQuantity();
                BigDecimal price = item.getLockedUnitPrice() == null
                    ? item.getDraftUnitPrice()
                    : item.getLockedUnitPrice();
                ScmExcel.setNumber(row.getCell(4), quantity);
                ScmExcel.setNumber(row.getCell(5), price);
                if (quantity != null && price != null) {
                    int n = index + 5;
                    String gross = "ROUND(E" + n + "*F" + n + ",4)";
                    BigDecimal discount = discounts.get(item.getItemId());
                    if (discount != null && discount.signum() > 0 && item.getOrderedLineAmount() != null
                        && item.getOrderedLineAmount().signum() > 0) {
                        // 复用正式优惠的按行毛额比例口径；辅助值隐藏，数量变化时Excel仍可正确复算。
                        ScmExcel.setNumber(row.createCell(8), discount);
                        ScmExcel.setNumber(row.createCell(9), item.getOrderedLineAmount());
                        row.getCell(6).setCellFormula("ROUND(" + gross + "-MIN(" + gross + ",MAX(0,ROUND(" + gross
                            + "*I" + n + "/J" + n + ",4))),4)");
                    } else {
                        row.getCell(6).setCellFormula(gross);
                    }
                } else {
                    row.getCell(6).setBlank();
                }
                row.getCell(4).setCellStyle(quantityStyle);
                row.getCell(5).setCellStyle(moneyStyle);
                row.getCell(6).setCellStyle(moneyStyle);
                row.getCell(7).setCellValue(item.getRemark());
                complete &= quantity != null && price != null;
            }
            sheet.setColumnHidden(8, true);
            sheet.setColumnHidden(9, true);
            var total = sheet.getRow(66 + extra).getCell(6);
            // 部分明细待称重/核价时不把已有行之和冒充整单合计。
            if (complete)
                total.setCellFormula("ROUND(SUM(G5:G" + (66 + extra) + "),4)");
            else
                total.setBlank();
            sheet.setRepeatingRows(org.apache.poi.ss.util.CellRangeAddress.valueOf("1:4"));
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            book.setPrintArea(0, 0, 7, 0, 67 + extra);
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            book.write(output);
            return output.toByteArray();
        }
    }
}
