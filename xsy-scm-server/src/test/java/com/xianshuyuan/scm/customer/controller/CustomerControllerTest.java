package com.xianshuyuan.scm.customer.controller;

import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.service.*;
import com.xianshuyuan.scm.customer.vo.OrderableSkuResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerService service;
    @MockitoBean CustomerQueryService query;
    @MockitoBean OrderableSkuQueryService orderableSkus;

    @Test void returnsPageEnvelope() throws Exception {
        given(query.page(any())).willReturn(new PageData<>(List.of(), 1, 20, 0));
        mvc.perform(get("/api/customers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test void mapsConflictToStableCode() throws Exception {
        willThrow(new BusinessException(CustomerErrorCodes.VERSION_CONFLICT))
            .given(service).updateStatus(eq(1L), eq(2), any());
        mvc.perform(put("/api/customers/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"status\":\"DISABLED\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(40934));
    }

    @Test void exposesOrderableSkusForCustomer() throws Exception {
        given(orderableSkus.listOrderable(1L)).willReturn(List.of(
            new OrderableSkuResponse(9L, 3L, "SKU-9", "Red / Large", "kg", "12.3400")
        ));
        mvc.perform(get("/api/customers/1/skus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(9))
            .andExpect(jsonPath("$.data[0].marketPrice").value("12.3400"));
    }
}
