package com.xianshuyuan.scm.customer.controller;

import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.customer.service.CustomerTypePriceService;
import com.xianshuyuan.scm.customer.service.CustomerTypePriceBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerTypePriceController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerTypePriceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerTypePriceService service;
    @MockitoBean CustomerTypePriceBatchService batches;

    @Test void exposesPaginatedCustomerTypePrices() throws Exception {
        given(service.page(anyLong(), anyLong(), any(), any(), any())).willReturn(new PageData<>(List.of(), 1, 20, 0));

        mvc.perform(get("/api/customer-type-prices?customerTypeId=3&keyword=菠菜"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());

        verify(service).page(1, 20, 3L, null, "菠菜");
    }
}
