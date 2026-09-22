package net.lab1024.sa.admin.module.scm.order;

import net.lab1024.sa.admin.module.scm.order.controller.*;
import net.lab1024.sa.admin.module.scm.order.service.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode;
import net.lab1024.sa.admin.module.scm.pricing.service.PriceResolver;
import net.lab1024.sa.base.common.domain.SystemEnvironment;
import net.lab1024.sa.base.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import cn.dev33.satoken.annotation.SaCheckPermission;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SalesOrderController.class, SalesOrderImportController.class, OrderReturnController.class, OrderRefundController.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {SalesOrderController.class, SalesOrderImportController.class, OrderReturnController.class, OrderRefundController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
class OrderWebTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    SalesOrderService service;
    @MockitoBean
    SalesOrderQueryService queries;
    @MockitoBean
    OrderReturnService returns;
    @MockitoBean
    OrderRefundService refunds;
    @MockitoBean
    PriceResolver prices;
    @MockitoBean
    SalesOrderImportService imports;
    @MockitoBean
    net.lab1024.sa.base.module.support.securityprotect.service.SecurityFileService securityFiles;
    @MockitoBean(name = "systemEnvironment")
    SystemEnvironment environment;

    @Test
    void allTwentyFiveEndpointsHaveNativePermissions() {
        int count = 0;
        for (var type : java.util.List.of(SalesOrderController.class, SalesOrderImportController.class, OrderReturnController.class, OrderRefundController.class))
            for (var m : type.getDeclaredMethods())
                if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                    assertThat(m.getAnnotation(SaCheckPermission.class)).as(m.getName()).isNotNull();
                    count++;
                }
        assertThat(count).isEqualTo(26);
    }

    @Test
    void importTemplateIsPackagedAndMatchesParserContract() throws Exception {
        var response = mvc.perform(get("/scm/order/import/template")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("%E9%94%80%E5%94%AE%E8%AE%A2%E5%8D%95%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF.xlsx")))
                // RFC 6266：中文名以 filename*=UTF-8'' 为准，filename= 仅作 ASCII 回退。
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andReturn().getResponse();
        assertThat(response.getContentAsByteArray()).isNotEmpty();
        assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(response.getContentAsByteArray().length));
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            var headerRow = sheet.getRow(0);
            var example = sheet.getRow(1);
            assertThat(java.util.stream.IntStream.range(0, 12).mapToObj(i -> headerRow.getCell(i).getStringCellValue()).toList()).containsExactly(
                    "模板版本", "导入订单标识", "客户编码", "收货人", "联系电话", "收货地址", "期望配送时间", "SKU编码", "下单数量", "人工单价", "改价原因", "订单备注");
            assertThat(example.getCell(0).getStringCellValue()).isEqualTo("1.0");
            assertThat(example.getCell(2).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(example.getCell(4).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(example.getCell(7).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            // 编码/电话列必须是文本格式，否则 Excel 会把 13800000000 变成科学计数、把前导零吃掉。
            for (int column : new int[]{2, 4, 7}) {
                assertThat(example.getCell(column).getCellStyle().getDataFormatString()).as("列" + column).isEqualTo("@");
            }
        }
    }

    @Test
    void detailUsesFourDecimalStringsAndKeepsNull() throws Exception {
        var o = new SalesOrderDetailVO();
        o.setOrderId(1L);
        o.setOrderedTotalAmount(null);
        o.setSettlementTotalAmount(new java.math.BigDecimal("0"));
        when(queries.detail(1L)).thenReturn(o);
        mvc.perform(get("/scm/order/detail/1")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.orderedTotalAmount").doesNotExist()).andExpect(jsonPath("$.data.settlementTotalAmount").value("0.0000"));
    }

    @Test
    void recentPricesEndpointIsReadOnlyAndSerializesFixedScale() throws Exception {
        var p = new OrderRecentPriceVO();
        p.setItemId(100L);
        p.setOrderId(7L);
        p.setOrderNo("SO-1");
        p.setCreatedAt(java.time.OffsetDateTime.parse("2026-09-20T00:00:00Z"));
        p.setConfirmedAt(java.time.OffsetDateTime.parse("2026-09-20T01:00:00Z"));
        p.setOrderSource("ADMIN");
        p.setOrderedQuantity(new java.math.BigDecimal("3"));
        p.setUnitPrice(new java.math.BigDecimal("8.5"));
        p.setPriceSource("MARKET");
        p.setSaleUnit("kg");
        when(queries.recentPrices(1L, 2L, 5)).thenReturn(java.util.List.of(p));
        mvc.perform(get("/scm/order/reference/recent-prices").param("customerId", "1").param("skuId", "2").param("limit", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].itemId").value(100))
                .andExpect(jsonPath("$.data[0].saleUnit").value("kg"))
                // confirmedAt 是排序键字段；其序列化随 ObjectMapper 时区配置变化，故只断言序列化为字符串，不比字面偏移量
                .andExpect(jsonPath("$.data[0].confirmedAt").value(org.hamcrest.Matchers.instanceOf(String.class)))
                .andExpect(jsonPath("$.data[0].unitPrice").value("8.5000"))
                .andExpect(jsonPath("$.data[0].orderedQuantity").value("3.0000"))
                .andExpect(jsonPath("$.data[0].priceSource").value("MARKET"));
        // 只读旁证：不得触碰写命令服务，也不得触发任何定价解析
        verifyNoInteractions(service, prices);
    }

    @Test
    void missingIdempotencyReturnsStableBusinessCode() throws Exception {
        when(service.submit(any(), isNull())).thenThrow(new ScmBusinessException(OrderErrorCode.ORDER_IDEMPOTENCY_KEY_REQUIRED));
        mvc.perform(post("/scm/order/submit").contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":1,\"version\":0}")).andExpect(jsonPath("$.code").value(40069));
    }

    @Test
    void jsonNumbersCannotEnterFixedPointQuantity() throws Exception {
        mvc.perform(post("/scm/order/item/actual-quantity").contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":1,\"itemId\":2,\"version\":0,\"actualQuantity\":1.2,\"reason\":\"称重\"}")).andExpect(jsonPath("$.code").value(30001));
        verifyNoInteractions(service);
    }

    @Test
    void afterSalesRejectsMissingVersionAndEmptyItems() throws Exception {
        mvc.perform(post("/scm/order/return/approve").contentType(MediaType.APPLICATION_JSON).content("{\"returnId\":1,\"version\":0,\"items\":[]}")).andExpect(jsonPath("$.code").value(30001));
        mvc.perform(post("/scm/order/refund/complete").contentType(MediaType.APPLICATION_JSON).content("{\"refundId\":1}")).andExpect(jsonPath("$.code").value(30001));
        verifyNoInteractions(returns, refunds);
    }
}
