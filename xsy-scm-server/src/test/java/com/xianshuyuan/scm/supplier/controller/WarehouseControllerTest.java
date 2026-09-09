package com.xianshuyuan.scm.supplier.controller;

import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import com.xianshuyuan.scm.supplier.dto.MasterDataStatusRequest;
import com.xianshuyuan.scm.supplier.service.WarehouseService;
import com.xianshuyuan.scm.supplier.vo.WarehouseVO;
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

@WebMvcTest(WarehouseController.class)
@AutoConfigureMockMvc(addFilters = false)
class WarehouseControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WarehouseService service;

    @Test
    void listsAndDetailsUsingStableWarehouseVo() throws Exception {
        WarehouseVO warehouse = new WarehouseVO(1L, "W-1", "仓库", EnabledStatus.ENABLED, 0, "地址", null);
        given(service.listWarehouses()).willReturn(List.of(warehouse));
        given(service.warehouseView(1L)).willReturn(warehouse);

        mvc.perform(get("/api/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].warehouseCode").value("W-1"));
        mvc.perform(get("/api/warehouses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("仓库"));
    }

    @Test
    void changesWarehouseStatusExplicitly() throws Exception {
        mvc.perform(post("/api/warehouses/1/status")
                        .contentType("application/json")
                        .content("{\"version\":0,\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        verify(service).updateWarehouseStatus(1L,
                new MasterDataStatusRequest(0, EnabledStatus.DISABLED));
    }
}
