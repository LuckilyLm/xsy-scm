package net.lab1024.sa.admin.module.scm.product;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.product.controller.ProductController;
import net.lab1024.sa.admin.module.scm.product.service.*;
import net.lab1024.sa.base.common.domain.*;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters=false)
@ContextConfiguration(classes={ProductController.class,ScmExceptionHandler.class,GlobalExceptionHandler.class})
class ProductControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductSpuService service;
    @MockitoBean ProductQueryService query;
    @MockitoBean(name="systemEnvironment") SystemEnvironment environment;
    @Test void returnsSmartAdminPaginationEnvelope() throws Exception {
        var page=new PageResult<>(); page.setPageNum(1L); page.setPageSize(20L); page.setTotal(0L); page.setPages(0L); page.setList(List.of()); page.setEmptyFlag(true);
        doReturn(page).when(query).query(any());
        mvc.perform(post("/scm/product/query").contentType(MediaType.APPLICATION_JSON).content("{\"pageNum\":1,\"pageSize\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.pageNum").value(1)).andExpect(jsonPath("$.data.emptyFlag").value(true));
    }
    @Test void validatesPageBoundsAndEmptySku() throws Exception {
        for (String body:List.of("{\"pageNum\":1,\"pageSize\":501}","{\"pageNum\":0,\"pageSize\":20}"))
            mvc.perform(post("/scm/product/query").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(jsonPath("$.code").value(30001));
        mvc.perform(post("/scm/product/add").contentType(MediaType.APPLICATION_JSON).content("{\"skuList\":[]}"))
                .andExpect(jsonPath("$.code").value(30001));
    }
    @Test void returnsHttp200With40921AndPreservesMissingProductCode() throws Exception {
        doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).delete(any());
        mvc.perform(post("/scm/product/delete").contentType(MediaType.APPLICATION_JSON).content("{\"spuId\":1,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40921)).andExpect(jsonPath("$.ok").value(false));
        when(query.detail(1L)).thenThrow(new ScmBusinessException(PRODUCT_NOT_FOUND));
        mvc.perform(get("/scm/product/detail/1")).andExpect(jsonPath("$.code").value(40420));
    }
    @Test void leavesSmartAdminBusinessAndUnexpectedExceptionsWithGlobalHandler() throws Exception {
        when(environment.isProd()).thenReturn(true);
        when(query.detail(1L)).thenThrow(new BusinessException("system module error"));
        mvc.perform(get("/scm/product/detail/1")).andExpect(jsonPath("$.code").value(10001)).andExpect(jsonPath("$.msg").value("system module error"));
        when(query.detail(2L)).thenThrow(new IllegalStateException("private details"));
        mvc.perform(get("/scm/product/detail/2")).andExpect(jsonPath("$.code").value(10001));
    }
}
