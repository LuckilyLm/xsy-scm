package net.lab1024.sa.admin.module.scm.supplier;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.supplier.controller.SupplierController;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierUpdateForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierVO;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierQueryService;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.SystemEnvironment;
import net.lab1024.sa.base.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_CODE_DUPLICATE;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 供应商端点契约测试。 */
@WebMvcTest(SupplierController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {SupplierController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
class SupplierControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SupplierService service;

    @MockitoBean
    private SupplierQueryService queryService;

    @MockitoBean(name = "systemEnvironment")
    private SystemEnvironment environment;

    @Test
    @DisplayName("列表返回分页信封")
    void returnsEnvelope() throws Exception {
        PageResult<SupplierVO> page = new PageResult<>();
        page.setPageNum(1L);
        page.setPageSize(20L);
        page.setTotal(0L);
        page.setPages(0L);
        page.setList(List.of());
        page.setEmptyFlag(true);
        when(queryService.query(any())).thenReturn(page);

        mvc.perform(post("/scm/supplier/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @DisplayName("新增返回主键 Long")
    void addReturnsId() throws Exception {
        when(service.add(any())).thenReturn(77L);

        mvc.perform(post("/scm/supplier/add").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierCode\":\"S001\",\"name\":\"基地\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(77));
    }

    @Test
    @DisplayName("供应商不存在 → 40440")
    void mapsNotFound() throws Exception {
        when(queryService.detail(1L)).thenThrow(new ScmBusinessException(SUPPLIER_NOT_FOUND));

        mvc.perform(get("/scm/supplier/detail/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40440));
    }

    @Test
    @DisplayName("供应商编码重复 → 40944")
    void mapsDuplicateCode() throws Exception {
        doThrow(new ScmBusinessException(SUPPLIER_CODE_DUPLICATE)).when(service).add(any());

        mvc.perform(post("/scm/supplier/add").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierCode\":\"S001\",\"name\":\"基地\"}"))
                .andExpect(jsonPath("$.code").value(40944));
    }

    @Test
    @DisplayName("乐观锁冲突 → 40921")
    void mapsVersionConflict() throws Exception {
        doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).update(any());

        mvc.perform(post("/scm/supplier/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":1,\"version\":0,\"supplierCode\":\"S001\",\"name\":\"基地\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40921));
    }

    @Test
    @DisplayName("更新体即使带 status 也不会被 DTO 接收（legacy 不变量 S6）")
    void updateFormCannotCarryStatus() throws Exception {
        mvc.perform(post("/scm/supplier/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":1,\"version\":0,\"supplierCode\":\"S001\",\"name\":\"基地\",\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 契约层面证明：SupplierUpdateForm 及其父类都没有 status 属性
        List<String> fieldNames = new java.util.ArrayList<>();
        for (Class<?> clazz = SupplierUpdateForm.class; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            Arrays.stream(clazz.getDeclaredFields()).map(Field::getName).forEach(fieldNames::add);
        }
        assertThat(fieldNames).doesNotContain("status");
    }

    @Test
    @DisplayName("新增体同样不接受 status：初始状态由服务端固定为 ENABLED")
    void addFormCannotCarryStatus() {
        List<String> fieldNames = new java.util.ArrayList<>();
        Class<?> clazz = net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierAddForm.class;
        for (; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            Arrays.stream(clazz.getDeclaredFields()).map(Field::getName).forEach(fieldNames::add);
        }
        assertThat(fieldNames).doesNotContain("status");
    }
}
