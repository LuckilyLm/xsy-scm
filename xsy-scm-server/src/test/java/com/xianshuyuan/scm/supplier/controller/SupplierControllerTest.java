package com.xianshuyuan.scm.supplier.controller;

import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import com.xianshuyuan.scm.supplier.service.SupplierService;
import com.xianshuyuan.scm.supplier.service.SupplierSkuService;
import com.xianshuyuan.scm.supplier.vo.SupplierSkuVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupplierController.class)
@AutoConfigureMockMvc(addFilters = false)
class SupplierControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SupplierService suppliers;

    @MockitoBean
    private SupplierSkuService supplierSkus;

    @Test
    void listsSupplierSkuConfigurationWithDecimalStrings() throws Exception {
        given(supplierSkus.listForSupplier(1L)).willReturn(List.of(new SupplierSkuVO(
                3L, 1L, 2L, "S1", "供应商", "SKU2", "大",
                Map.of("规格", "大"), "箱", "12.3400", null, true,
                EnabledStatus.ENABLED, 4)));

        mvc.perform(get("/api/suppliers/1/skus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].referencePrice").value("12.3400"));
    }

    @Test
    void replacesSupplierSkuConfiguration() throws Exception {
        mvc.perform(put("/api/suppliers/1/skus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"supplierId\":1,\"skuId\":2,\"purchaseUnit\":\"箱\","
                                + "\"referencePrice\":\"12.3400\",\"defaultSupplier\":true,"
                                + "\"status\":\"ENABLED\"}]"))
                .andExpect(status().isOk());

        verify(supplierSkus).replaceForSupplier(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void rejectsNumericReferencePrice() throws Exception {
        mvc.perform(put("/api/suppliers/1/skus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"supplierId\":1,\"skuId\":2,\"purchaseUnit\":\"箱\","
                                + "\"referencePrice\":12.3400,\"defaultSupplier\":false}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        verifyNoInteractions(supplierSkus);
    }

    @Test
    void rejectsReferencePriceWithExcessiveScale() throws Exception {
        mvc.perform(put("/api/suppliers/1/skus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"supplierId\":1,\"skuId\":2,\"purchaseUnit\":\"箱\","
                                + "\"referencePrice\":\"12.34000\",\"defaultSupplier\":false}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        verifyNoInteractions(supplierSkus);
    }

    @Test
    void acceptsEmptyReplacementAsClearAll() throws Exception {
        mvc.perform(put("/api/suppliers/1/skus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk());

        verify(supplierSkus).replaceForSupplier(1L, List.of());
    }
}
