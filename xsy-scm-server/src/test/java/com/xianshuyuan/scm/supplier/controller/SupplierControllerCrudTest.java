package com.xianshuyuan.scm.supplier.controller;

import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import com.xianshuyuan.scm.supplier.service.SupplierService;
import com.xianshuyuan.scm.supplier.service.SupplierSkuService;
import com.xianshuyuan.scm.supplier.vo.SupplierSkuVO;
import com.xianshuyuan.scm.supplier.vo.SupplierVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupplierController.class)
@AutoConfigureMockMvc(addFilters = false)
class SupplierControllerCrudTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SupplierService suppliers;

    @MockitoBean
    private SupplierSkuService supplierSkus;

    @Test
    void listsAndDetailsUsingStableSupplierVo() throws Exception {
        SupplierVO supplier = new SupplierVO(1L, "S-1", "供应商", EnabledStatus.ENABLED, 0, null);
        given(suppliers.listSuppliers()).willReturn(List.of(supplier));
        given(suppliers.supplierView(1L)).willReturn(supplier);

        mvc.perform(get("/api/suppliers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].supplierCode").value("S-1"));
        mvc.perform(get("/api/suppliers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("供应商"));
    }

    @Test
    void changesSupplierStatusExplicitly() throws Exception {
        mvc.perform(post("/api/suppliers/1/status")
                        .contentType("application/json")
                        .content("{\"version\":0,\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        verify(suppliers).updateSupplierStatus(1L, 0, EnabledStatus.DISABLED);
    }
}
