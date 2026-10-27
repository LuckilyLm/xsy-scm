package com.xsy.scm.businesscategory;

import com.xsy.scm.businesscategory.controller.BusinessCategoryController;
import com.xsy.scm.businesscategory.domain.vo.BusinessCategoryTreeVO;
import com.xsy.scm.businesscategory.service.BusinessCategoryService;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.handler.ScmExceptionHandler;
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

import static com.xsy.scm.businesscategory.constant.BusinessCategoryErrorCode.BUSINESS_CATEGORY_NOT_FOUND;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户 / 供应商分类端点契约测试。
 */
@WebMvcTest(BusinessCategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {BusinessCategoryController.class, ScmExceptionHandler.class,
    GlobalExceptionHandler.class})
class BusinessCategoryControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BusinessCategoryService service;

    @MockitoBean(name = "systemEnvironment")
    private SystemEnvironment environment;

    @Test
    @DisplayName("树接口按 scope 返回数组信封")
    void treeReturnsEnvelope() throws Exception {
        when(service.tree("CUSTOMER")).thenReturn(List.of(new BusinessCategoryTreeVO()));

        mvc.perform(post("/scm/business-category/tree").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"CUSTOMER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("新增返回分类主键")
    void addReturnsCategoryId() throws Exception {
        when(service.add(any())).thenReturn(88L);

        mvc.perform(post("/scm/business-category/add").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"CUSTOMER\",\"categoryCode\":\"VEG\",\"name\":\"蔬菜\","
                    + "\"sortOrder\":0,\"status\":\"ENABLED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data").value(88));
    }

    @Test
    @DisplayName("分类不存在 → 40444")
    void mapsNotFound() throws Exception {
        when(service.detail(1L)).thenThrow(new ScmBusinessException(BUSINESS_CATEGORY_NOT_FOUND));

        mvc.perform(get("/scm/business-category/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(40444));
    }

    @Test
    @DisplayName("非法作用域被参数校验拦下")
    void rejectsUnknownScope() throws Exception {
        mvc.perform(post("/scm/business-category/tree").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"UNKNOWN\"}"))
            .andExpect(jsonPath("$.code").value(30001));
    }
}
