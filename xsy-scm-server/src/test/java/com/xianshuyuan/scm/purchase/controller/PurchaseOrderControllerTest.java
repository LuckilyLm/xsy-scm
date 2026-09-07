package com.xianshuyuan.scm.purchase.controller;

import com.xianshuyuan.scm.purchase.entity.PurchaseOrderStatus;
import com.xianshuyuan.scm.purchase.service.PurchaseOrderService;
import com.xianshuyuan.scm.purchase.vo.PurchaseOrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(PurchaseOrderController.class)
class PurchaseOrderControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean PurchaseOrderService service;

    @Test
    void createRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON).content(validOrder()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void createPassesIdempotencyKeyToService() throws Exception {
        when(service.create(any(), eq("create-key"))).thenReturn(10L);

        mockMvc.perform(post("/api/purchase-orders").header("Idempotency-Key", "create-key")
                        .contentType(MediaType.APPLICATION_JSON).content(validOrder()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(10));
        verify(service).create(any(), eq("create-key"));
    }

    @Test
    void submitRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/10/submit")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void cancelRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/purchase-orders/10/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"duplicate\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void detailSerializesTypedResponseAndDecimalStrings() throws Exception {
        when(service.detail(10L)).thenReturn(new PurchaseOrderResponse(10L, 2, "PO-1", 1L, "S1", "Supplier",
                2L, "W1", "Warehouse", 7L, null, null, PurchaseOrderStatus.DRAFT,
                "7.0000", null, null, null, List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/purchase-orders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.orderNo").value("PO-1"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalAmount").value("7.0000"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.allocations").isArray())
                .andExpect(jsonPath("$.data.operationLogs").isArray());
    }

    private static String validOrder() {
        return """
                {"supplierId":1,"warehouseId":2,"items":[{"skuId":3,"quantity":"2.0000","price":"3.5000","demandId":30}]}
                """;
    }
}
