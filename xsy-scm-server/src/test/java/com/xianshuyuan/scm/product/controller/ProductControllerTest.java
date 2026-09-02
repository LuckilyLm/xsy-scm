package com.xianshuyuan.scm.product.controller;

import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.product.service.ProductApplicationService;
import com.xianshuyuan.scm.product.service.ProductErrorCodes;
import com.xianshuyuan.scm.product.service.ProductQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductQueryService queryService;

    @MockitoBean
    private ProductApplicationService applicationService;

    @Test
    void returnsStandardPaginationEnvelope() throws Exception {
        given(queryService.page(any())).willReturn(new PageData<>(List.of(), 1, 20, 0));

        mockMvc.perform(get("/api/products").param("page", "1").param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.records").isArray())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(20));
    }

    @Test
    void rejectsPageSizeOutsideBoundary() throws Exception {
        mockMvc.perform(get("/api/products").param("pageSize", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void rejectsCreateWithoutSku() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "spuCode":"SPU-EMPTY",
                      "name":"空规格商品",
                      "categoryId":30,
                      "status":"OFF_SHELF",
                      "skus":[]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40000))
            .andExpect(jsonPath("$.message").value("商品至少需要一个 SKU"));
    }

    @Test
    void mapsMissingProductTo404() throws Exception {
        given(queryService.get(999L)).willThrow(new BusinessException(ProductErrorCodes.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/products/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(40420));
    }

    @Test
    void mapsOptimisticConflictTo409() throws Exception {
        willThrow(new BusinessException(ProductErrorCodes.VERSION_CONFLICT))
            .given(applicationService).updateStatus(eq(1L), eq(3), any());

        mockMvc.perform(put("/api/products/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":3,\"status\":\"OFF_SHELF\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(40921));
    }
}
