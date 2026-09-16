package net.lab1024.sa.admin.module.scm.order;

import net.lab1024.sa.admin.module.scm.order.controller.*;
import net.lab1024.sa.admin.module.scm.order.service.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode;
import net.lab1024.sa.admin.module.scm.pricing.service.PriceResolver;
import net.lab1024.sa.base.common.domain.SystemEnvironment;
import net.lab1024.sa.base.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import cn.dev33.satoken.annotation.SaCheckPermission;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SalesOrderController.class,OrderReturnController.class,OrderRefundController.class})
@AutoConfigureMockMvc(addFilters=false)
@ContextConfiguration(classes={SalesOrderController.class,OrderReturnController.class,OrderRefundController.class,ScmExceptionHandler.class,GlobalExceptionHandler.class})
class OrderWebTest {
    @Autowired MockMvc mvc;
    @MockitoBean SalesOrderService service;
    @MockitoBean SalesOrderQueryService queries;
    @MockitoBean OrderReturnService returns;
    @MockitoBean OrderRefundService refunds;
    @MockitoBean PriceResolver prices;
    @MockitoBean(name="systemEnvironment") SystemEnvironment environment;
    @Test void allTwentyOneEndpointsHaveNativePermissions(){
        int count=0;for(var type:java.util.List.of(SalesOrderController.class,OrderReturnController.class,OrderRefundController.class))for(var m:type.getDeclaredMethods())if(java.lang.reflect.Modifier.isPublic(m.getModifiers())){assertThat(m.getAnnotation(SaCheckPermission.class)).as(m.getName()).isNotNull();count++;}assertThat(count).isEqualTo(21);
    }
    @Test void detailUsesFourDecimalStringsAndKeepsNull() throws Exception {
        var o=new SalesOrderDetailVO();o.setOrderId(1L);o.setOrderedTotalAmount(null);o.setSettlementTotalAmount(new java.math.BigDecimal("0"));when(queries.detail(1L)).thenReturn(o);
        mvc.perform(get("/scm/order/detail/1")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.orderedTotalAmount").doesNotExist()).andExpect(jsonPath("$.data.settlementTotalAmount").value("0.0000"));
    }
    @Test void missingIdempotencyReturnsStableBusinessCode() throws Exception {
        when(service.submit(any(),isNull())).thenThrow(new ScmBusinessException(OrderErrorCode.ORDER_IDEMPOTENCY_KEY_REQUIRED));
        mvc.perform(post("/scm/order/submit").contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":1,\"version\":0}")).andExpect(jsonPath("$.code").value(40069));
    }
    @Test void jsonNumbersCannotEnterFixedPointQuantity() throws Exception {
        mvc.perform(post("/scm/order/item/actual-quantity").contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":1,\"itemId\":2,\"version\":0,\"actualQuantity\":1.2,\"reason\":\"称重\"}")).andExpect(jsonPath("$.code").value(30001));verifyNoInteractions(service);
    }
    @Test void afterSalesRejectsMissingVersionAndEmptyItems() throws Exception {
        mvc.perform(post("/scm/order/return/approve").contentType(MediaType.APPLICATION_JSON).content("{\"returnId\":1,\"version\":0,\"items\":[]}")).andExpect(jsonPath("$.code").value(30001));
        mvc.perform(post("/scm/order/refund/complete").contentType(MediaType.APPLICATION_JSON).content("{\"refundId\":1}")).andExpect(jsonPath("$.code").value(30001));verifyNoInteractions(returns,refunds);
    }
}
