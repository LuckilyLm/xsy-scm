package com.xianshuyuan.scm.order.controller;

import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.order.service.AfterSalesApplicationService;
import com.xianshuyuan.scm.order.service.AfterSalesQueryService;
import com.xianshuyuan.scm.order.entity.OrderRefundStatus;
import com.xianshuyuan.scm.order.entity.OrderReturnStatus;
import com.xianshuyuan.scm.order.vo.OrderRefundResponse;
import com.xianshuyuan.scm.order.vo.OrderReturnResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({OrderReturnController.class, OrderRefundController.class})
class AfterSalesControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AfterSalesApplicationService commands;
    @MockitoBean AfterSalesQueryService query;

    @Test void returnListIsPaginatedAndFilterable() throws Exception {
        when(query.pageReturns(any())).thenReturn(new PageData<>(List.of(), 2, 10, 21));
        mockMvc.perform(get("/api/order-returns?page=2&pageSize=10&keyword=RT&status=PENDING&orderId=7"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.total").value(21));
        verify(query).pageReturns(any());
    }

    @Test void refundListIsPaginatedAndFilterable() throws Exception {
        var refund = new OrderRefundResponse(6L, "RF1", 5L, 7L, 3L,
            "12.3400", OrderRefundStatus.COMPLETED, "EXT-1", OffsetDateTime.parse("2026-09-04T00:00:00Z"), 1);
        when(query.pageRefunds(any())).thenReturn(new PageData<>(List.of(refund), 1, 20, 3));
        mockMvc.perform(get("/api/order-refunds?page=1&pageSize=20&keyword=RF&status=COMPLETED&orderId=7"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.records[0].refundAmount").value("12.3400"));
    }

    @Test void returnAmountsAreSerializedAsDecimalStrings() throws Exception {
        var item = new OrderReturnResponse.Item(11L, 21L, "3.0000", "1.5000", "5.0000", "7.5000", 1);
        var orderReturn = new OrderReturnResponse(5L, "RT1", 7L, 3L, OrderReturnStatus.APPROVED,
            "破损", null, "7.5000", 1, OffsetDateTime.parse("2026-09-04T00:00:00Z"), List.of(item), null);
        when(query.getReturn(5L)).thenReturn(orderReturn);

        mockMvc.perform(get("/api/order-returns/5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.approvedAmount").value("7.5000"))
            .andExpect(jsonPath("$.data.items[0].requestedQuantity").value("3.0000"));
    }

    @Test void approvalAcceptsEveryLinePayload() throws Exception {
        mockMvc.perform(post("/api/order-returns/5/approve").header("Idempotency-Key", " approve-5 ")
                .contentType("application/json")
                .content("{\"version\":1,\"items\":[{\"returnItemId\":11,\"version\":2,\"approvedQuantity\":\"1.5000\"},{\"returnItemId\":12,\"version\":0,\"approvedQuantity\":\"2.0000\"}]}"))
            .andExpect(status().isOk());
        verify(commands).approve(any(), any(Long.class), any());
    }
}
