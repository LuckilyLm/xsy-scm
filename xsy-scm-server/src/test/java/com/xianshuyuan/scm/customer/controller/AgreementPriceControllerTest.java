package com.xianshuyuan.scm.customer.controller;

import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.customer.service.AgreementPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgreementPriceController.class)
class AgreementPriceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AgreementPriceService service;

    @Test void exposesPaginatedAgreementPricesOnCanonicalPath() throws Exception {
        given(service.page(anyLong(), anyLong(), any(), any(), any())).willReturn(new PageData<>(List.of(), 1, 20, 0));
        mvc.perform(get("/api/customer-agreement-prices?keyword=春风"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records").isArray());
        verify(service).page(1, 20, null, null, "春风");
    }

    @Test void preservesLegacyAgreementPricePath() throws Exception {
        given(service.page(anyLong(), anyLong(), any(), any(), any())).willReturn(new PageData<>(List.of(), 1, 20, 0));
        mvc.perform(get("/api/agreement-prices"))
            .andExpect(status().isOk());
    }
}
