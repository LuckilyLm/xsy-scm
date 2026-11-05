package com.xsy.scm.print;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.print.service.CustomerSheetExcelService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

/** 客户原版工作簿：数量来源、金额可计算、行备注和签字栏不丢失。 */
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
