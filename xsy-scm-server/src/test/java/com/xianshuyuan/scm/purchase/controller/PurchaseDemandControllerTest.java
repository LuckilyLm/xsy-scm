package com.xianshuyuan.scm.purchase.controller;

import com.xianshuyuan.scm.purchase.service.PurchaseDemandService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PurchaseDemandController.class)
class PurchaseDemandControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PurchaseDemandService service;

    @Test
    void generateRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/purchase-demands/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salesOrderIds\":[1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void generatePassesKeyAndReturnsTypedIds() throws Exception {
        when(service.generate(any(), eq("generate-key"))).thenReturn(List.of(10L, 11L));

        mockMvc.perform(post("/api/purchase-demands/generate")
                        .header("Idempotency-Key", "generate-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salesOrderIds\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(10))
                .andExpect(jsonPath("$.data[1]").value(11));
        verify(service).generate(any(), eq("generate-key"));
    }
}
