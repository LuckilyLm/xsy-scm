package net.lab1024.sa.admin.module.scm.customer;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.customer.controller.CustomerController;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerVO;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerQueryService;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.SystemEnvironment;
import net.lab1024.sa.base.common.exception.BusinessException;
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
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_CODE_DUPLICATE;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_TRADABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户端点契约测试。
 *
 * <p>业务错误一律 HTTP 200 + {@code ResponseDTO.code}（W1 已批准决议 Q1）——
 * 前端 axios 拦截器只在 2xx 响应体上读 {@code code}/{@code msg}。
 */
@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {CustomerController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
class CustomerControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerService service;

    @MockitoBean
    private CustomerQueryService queryService;

    @MockitoBean(name = "systemEnvironment")
    private SystemEnvironment environment;

    private static PageResult<CustomerVO> emptyPage() {
        PageResult<CustomerVO> page = new PageResult<>();
        page.setPageNum(1L);
        page.setPageSize(20L);
        page.setTotal(0L);
        page.setPages(0L);
        page.setList(List.of());
        page.setEmptyFlag(true);
        return page;
    }

    @Test
    @DisplayName("列表返回 SmartAdmin 分页信封")
    void returnsPaginationEnvelope() throws Exception {
        when(queryService.query(any())).thenReturn(emptyPage());

        mvc.perform(post("/scm/customer/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.emptyFlag").value(true));
    }

    @Test
    @DisplayName("分页边界：缺 pageNum / pageSize=101 都被参数校验拦下")
    void validatesPageBounds() throws Exception {
        for (String body : List.of(
                "{\"pageSize\":20}",
                "{\"pageNum\":0,\"pageSize\":20}",
                "{\"pageNum\":1,\"pageSize\":101}")) {
            mvc.perform(post("/scm/customer/query").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(30001));
        }
    }

    @Test
    @DisplayName("缺少 version 的删除请求被拦下")
    void requiresVersionOnDelete() throws Exception {
        mvc.perform(post("/scm/customer/delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1}"))
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    @DisplayName("客户不存在 → 40430")
    void mapsNotFound() throws Exception {
        when(queryService.detail(1L)).thenThrow(new ScmBusinessException(CUSTOMER_NOT_FOUND));

        mvc.perform(get("/scm/customer/detail/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40430))
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    @DisplayName("乐观锁冲突 → 40921，且保持 HTTP 200")
    void mapsVersionConflict() throws Exception {
        doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).delete(any());

        mvc.perform(post("/scm/customer/delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40921))
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    @DisplayName("编码重复 → 40936")
    void mapsDuplicateCode() throws Exception {
        doThrow(new ScmBusinessException(CUSTOMER_CODE_DUPLICATE)).when(service).add(any());

        mvc.perform(post("/scm/customer/add").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerCode\":\"C001\",\"name\":\"测试\",\"customerTypeId\":1}"))
                .andExpect(jsonPath("$.code").value(40936));
    }

    @Test
    @DisplayName("SCM 业务码原样透出（以 40930 不可交易为例），不被降级为系统错误码")
    void passesThroughArbitraryScmCode() throws Exception {
        when(queryService.detail(9L)).thenThrow(new ScmBusinessException(CUSTOMER_NOT_TRADABLE));

        mvc.perform(get("/scm/customer/detail/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40930));
    }

    @Test
    @DisplayName("下拉接口返回数组信封")
    void returnsOptionList() throws Exception {
        when(queryService.optionList()).thenReturn(List.of());

        mvc.perform(post("/scm/customer/option/list").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("非 SCM 异常仍交给 SmartAdmin 全局处理器（不抢别人的异常）")
    void leavesNonScmExceptionsToGlobalHandler() throws Exception {
        when(environment.isProd()).thenReturn(true);
        when(queryService.detail(1L)).thenThrow(new BusinessException("system module error"));

        mvc.perform(get("/scm/customer/detail/1"))
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.msg").value("system module error"));

        when(queryService.detail(2L)).thenThrow(new IllegalStateException("private details"));

        mvc.perform(get("/scm/customer/detail/2"))
                .andExpect(jsonPath("$.code").value(10001));
    }
}
