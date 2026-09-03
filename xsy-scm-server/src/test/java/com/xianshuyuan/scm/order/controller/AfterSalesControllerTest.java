package com.xianshuyuan.scm.order.controller;

import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.order.service.AfterSalesApplicationService;
import com.xianshuyuan.scm.order.service.AfterSalesQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
        when(query.pageRefunds(any())).thenReturn(new PageData<>(List.of(), 1, 20, 3));
        mockMvc.perform(get("/api/order-refunds?page=1&pageSize=20&keyword=RF&status=COMPLETED&orderId=7"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3));
    }

    @Test void approvalAcceptsEveryLinePayload() throws Exception {
        mockMvc.perform(post("/api/order-returns/5/approve").header("Idempotency-Key", " approve-5 ")
                .contentType("application/json")
                .content("{\"version\":1,\"items\":[{\"returnItemId\":11,\"version\":2,\"approvedQuantity\":\"1.5000\"},{\"returnItemId\":12,\"version\":0,\"approvedQuantity\":\"2.0000\"}]}"))
            .andExpect(status().isOk());
        verify(commands).approve(any(), any(Long.class), any());
    }
}
