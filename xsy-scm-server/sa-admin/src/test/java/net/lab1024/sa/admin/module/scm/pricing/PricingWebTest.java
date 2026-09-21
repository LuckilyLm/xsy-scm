package net.lab1024.sa.admin.module.scm.pricing;

import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.customer.controller.CustomerVisibilityController;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerSkuVisibilityService;
import net.lab1024.sa.admin.module.scm.product.controller.ProductSkuController;
import net.lab1024.sa.admin.module.scm.product.service.ProductSkuOptionQueryService;
import net.lab1024.sa.admin.module.scm.pricing.controller.*;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.*;
import net.lab1024.sa.admin.module.scm.pricing.service.*;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.SystemEnvironment;
import net.lab1024.sa.base.handler.GlobalExceptionHandler;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W3 web contract coverage. Authentication filters are disabled; permission annotations remain on controllers.
 */
@WebMvcTest({AgreementPriceController.class, CustomerTypePriceController.class,
        PriceBatchController.class, PriceHistoryController.class, PriceResolveController.class,
        CustomerVisibilityController.class, ProductSkuController.class})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {AgreementPriceController.class, CustomerTypePriceController.class,
        PriceBatchController.class, PriceHistoryController.class, PriceResolveController.class,
        PriceHistoryController.class, CustomerVisibilityController.class, ProductSkuController.class,
        ScmExceptionHandler.class, GlobalExceptionHandler.class})
class PricingWebTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    AgreementPriceService agreementService;
    @MockitoBean
    AgreementPriceQueryService agreementQueries;
    @MockitoBean
    CustomerTypePriceService typeService;
    @MockitoBean
    CustomerTypePriceQueryService typeQueries;
    @MockitoBean
    PriceBatchService batchService;
    @MockitoBean
    PriceHistoryQueryService historyService;
    @MockitoBean
    PriceResolver resolver;
    @MockitoBean
    CustomerSkuVisibilityService visibilityService;
    @MockitoBean
    ProductSkuOptionQueryService skuService;
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
    void agreementQueryReturnsSmartAdminPageEnvelope() throws Exception {
        when(agreementQueries.query(any())).thenReturn(emptyPage());
        mvc.perform(post("/scm/pricing/agreement-price/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    void typePriceQueryReturnsPageEnvelope() throws Exception {
        when(typeQueries.query(any())).thenReturn(emptyPage());
        mvc.perform(post("/scm/pricing/type-price/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.emptyFlag").value(true));
    }

    @Test
    void batchRejectsMissingRowsAtBoundary() throws Exception {
        mvc.perform(post("/scm/pricing/type-price/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchKey\":\"B\",\"rows\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30001));
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
    void visibilityAndSkuOptionValidateRequests() throws Exception {
        mvc.perform(post("/scm/customer/visibility/reverse/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20,\"visibilityPolicy\":\"BROKEN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30001));
        mvc.perform(post("/scm/product/sku/option-list").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\":201}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30001));
    }
}
