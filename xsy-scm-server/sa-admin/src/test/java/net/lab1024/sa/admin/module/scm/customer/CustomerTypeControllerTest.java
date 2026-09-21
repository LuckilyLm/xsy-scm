package net.lab1024.sa.admin.module.scm.customer;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.customer.controller.CustomerTypeController;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerTypeVO;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerTypeService;
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

import java.util.List;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_TYPE_CODE_DUPLICATE;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_TYPE_IN_USE;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_TYPE_NOT_FOUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户类型端点契约测试。
 */
@WebMvcTest(CustomerTypeController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {CustomerTypeController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
class CustomerTypeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerTypeService service;

    @MockitoBean(name = "systemEnvironment")
    private SystemEnvironment environment;

    @Test
    @DisplayName("列表返回分页信封")
    void returnsEnvelope() throws Exception {
        PageResult<CustomerTypeVO> page = new PageResult<>();
        page.setPageNum(1L);
        page.setPageSize(20L);
        page.setTotal(0L);
        page.setPages(0L);
        page.setList(List.of());
        page.setEmptyFlag(true);
        when(service.query(any())).thenReturn(page);

        mvc.perform(post("/scm/customer/type/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @DisplayName("类型不存在 → 40431")
    void mapsNotFound() throws Exception {
        doThrow(new ScmBusinessException(CUSTOMER_TYPE_NOT_FOUND)).when(service).update(any());

        mvc.perform(post("/scm/customer/type/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":1,\"version\":0,\"typeCode\":\"A\",\"name\":\"甲\",\"status\":\"ENABLED\"}"))
                .andExpect(jsonPath("$.code").value(40431));
    }

    @Test
    @DisplayName("类型编码重复 → 40937")
    void mapsDuplicateCode() throws Exception {
        doThrow(new ScmBusinessException(CUSTOMER_TYPE_CODE_DUPLICATE)).when(service).add(any());

        mvc.perform(post("/scm/customer/type/add").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeCode\":\"A\",\"name\":\"甲\",\"status\":\"ENABLED\"}"))
                .andExpect(jsonPath("$.code").value(40937));
    }

    @Test
    @DisplayName("类型被客户引用不能删除 → 40938")
    void mapsInUse() throws Exception {
        doThrow(new ScmBusinessException(CUSTOMER_TYPE_IN_USE)).when(service).delete(any());

        mvc.perform(post("/scm/customer/type/delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":1,\"version\":0}"))
                .andExpect(jsonPath("$.code").value(40938));
    }

    @Test
    @DisplayName("乐观锁冲突 → 40921")
    void mapsVersionConflict() throws Exception {
        doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).delete(any());

        mvc.perform(post("/scm/customer/type/delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":1,\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40921));
    }

    @Test
    @DisplayName("新增时状态必填且取值域受限")
    void validatesStatus() throws Exception {
        mvc.perform(post("/scm/customer/type/add").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeCode\":\"A\",\"name\":\"甲\",\"status\":\"POTENTIAL\"}"))
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    @DisplayName("下拉只返回启用类型的数组")
    void returnsOptions() throws Exception {
        when(service.optionList()).thenReturn(List.of());

        mvc.perform(post("/scm/customer/type/option/list").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }
}
