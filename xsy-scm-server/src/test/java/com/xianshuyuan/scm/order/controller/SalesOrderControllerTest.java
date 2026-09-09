package com.xianshuyuan.scm.order.controller;

import com.xianshuyuan.scm.order.service.SalesOrderApplicationService;
import com.xianshuyuan.scm.order.service.SalesOrderQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SalesOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class SalesOrderControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean SalesOrderQueryService query;
    @MockitoBean SalesOrderApplicationService commands;

    @Test void createRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(validOrder()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
    }

    @Test void createAcceptsDecimalStringAndPassesKey() throws Exception {
        mockMvc.perform(post("/api/orders").header("Idempotency-Key","create-1").contentType(MediaType.APPLICATION_JSON).content(validOrder()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verify(commands).create(any(),eq("create-1"));
    }

    @Test void rejectsNumericQuantityAtHttpBoundary() throws Exception {
        mockMvc.perform(post("/api/orders").header("Idempotency-Key","create-2").contentType(MediaType.APPLICATION_JSON).content(validOrder().replace("\"2.0000\"","2.0")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
    }

    private static String validOrder(){return """
        {"customerId":1,"source":"NORMAL","items":[{"skuId":2,"orderedQuantity":"2.0000","manualPriceOverride":false}]}
        """;}
}
