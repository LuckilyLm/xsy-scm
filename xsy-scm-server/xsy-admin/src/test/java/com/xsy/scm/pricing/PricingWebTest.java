package com.xsy.scm.pricing;

import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.customer.controller.CustomerVisibilityController;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.customer.service.CustomerSkuVisibilityService;
import com.xsy.scm.product.controller.ProductSkuController;
import com.xsy.scm.product.service.ProductSkuOptionQueryService;
import com.xsy.scm.product.service.ProductSkuStockConversionService;
import com.xsy.scm.pricing.controller.*;
import com.xsy.scm.pricing.domain.vo.*;
import com.xsy.scm.pricing.service.*;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.SystemEnvironment;
import com.xsy.scm.base.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web contract coverage. Authentication filters are disabled; permission annotations remain on controllers.
 */
@WebMvcTest({AgreementPriceController.class, PriceHistoryController.class, PriceResolveController.class,
        CustomerVisibilityController.class, ProductSkuController.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {AgreementPriceController.class, PriceHistoryController.class,
        PriceResolveController.class, CustomerVisibilityController.class, ProductSkuController.class,
        ScmExceptionHandler.class, GlobalExceptionHandler.class})
class PricingWebTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    AgreementPriceService agreementService;
    @MockitoBean
    AgreementPriceQueryService agreementQueries;
    @MockitoBean
    PriceHistoryQueryService historyService;
    @MockitoBean
    PriceResolver resolver;
    @MockitoBean
    CustomerService customers;
    @MockitoBean
    CustomerSkuVisibilityService visibilityService;
    @MockitoBean
    ProductSkuOptionQueryService skuService;
    @MockitoBean
    ProductSkuStockConversionService skuConversionService;
    @MockitoBean
    ScmWarehouseScopeGuard warehouseScopeGuard;
    @MockitoBean(name = "systemEnvironment")
    SystemEnvironment environment;

    private static <T> PageResult<T> emptyPage() {
        PageResult<T> page = new PageResult<>();
        page.setPageNum(1L);
        page.setPageSize(20L);
        page.setTotal(0L);
        page.setPages(0L);
        page.setList(List.of());
        page.setEmptyFlag(true);
        return page;
    }

    @Test
    void agreementQueryReturnsXSYPageEnvelope() throws Exception {
        when(agreementQueries.query(any())).thenReturn(emptyPage());
        mvc.perform(post("/scm/pricing/agreement-price/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    void historyNormalizesInvalidSourceAndReturnsPage() throws Exception {
        when(historyService.query(any())).thenReturn(emptyPage());
        mvc.perform(post("/scm/pricing/history/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20,\"source\":\"UNKNOWN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void resolveRequiresCustomerAndSkuIds() throws Exception {
        mvc.perform(post("/scm/pricing/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"skuIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    void resolveRejectsCustomerOutsideCurrentScope() throws Exception {
        when(customers.requireInCurrentScope(1L)).thenThrow(new ScmDataScopeException());
        mvc.perform(post("/scm/pricing/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1,\"skuIds\":[2]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30005));
        verifyNoInteractions(resolver);
    }

    @Test
    void visibilityAndSkuOptionValidateRequests() throws Exception {
        mvc.perform(post("/scm/customer/visibility/reverse/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20,\"visibilityPolicy\":\"BROKEN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30001));
        mvc.perform(post("/scm/product/sku/option-list").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\":201}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30001));
    }
}
