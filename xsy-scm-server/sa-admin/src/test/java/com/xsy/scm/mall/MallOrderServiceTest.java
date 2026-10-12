package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.cart.service.MallCartService;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.order.domain.form.MallOrderCancelForm;
import com.xsy.scm.mall.order.domain.vo.MallReorderVO;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.mall.order.service.MallOrderService;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import com.xsy.scm.order.service.SalesOrderService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 商城订单写操作的边界：取消必须受正式状态机约束，再来一单必须重新校验且不复制历史价格。
 */
class MallOrderServiceTest {
    private static final Long ORDER_ID = 21L;

    private final MallOrderQueryService queryService = mock(MallOrderQueryService.class);
    private final SalesOrderService salesOrderService = mock(SalesOrderService.class);
    private final SalesOrderItemDao itemDao = mock(SalesOrderItemDao.class);
    private final MallCartService cartService = mock(MallCartService.class);
    private final MallCatalogService catalogService = mock(MallCatalogService.class);
    private final MallOrderService service = new MallOrderService(queryService, salesOrderService, itemDao,
            cartService, catalogService);
    private final RequestMallCustomer customer = customer();

    @Test
    void cancelPassesTheLockedVersionAndReasonToTheOrderService() {
        when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("PENDING", 4));
        when(queryService.activeFulfillmentStatus(ORDER_ID)).thenReturn(null);

        service.cancel(customer, ORDER_ID, cancelForm("下错单了"), "key");

        ArgumentCaptor<OrderCancelForm> captor = ArgumentCaptor.forClass(OrderCancelForm.class);
        verify(salesOrderService).cancelFromMall(captor.capture(), eq("key"));
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getVersion()).isEqualTo(4);
        assertThat(captor.getValue().getReason()).isEqualTo("下错单了");
    }

    @Test
    void confirmedOrderCannotBeCancelledByTheCustomer() {
        when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CONFIRMED", 1));

        assertThatThrownBy(() -> service.cancel(customer, ORDER_ID, cancelForm("不需要了"), "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.ORDER_NOT_CANCELABLE));
        verify(salesOrderService, never()).cancelFromMall(any(), any());
    }

    @Test
    void orderAlreadyInTransitCannotBeCancelled() {
        when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("PENDING", 1));
        when(queryService.activeFulfillmentStatus(ORDER_ID)).thenReturn("IN_TRANSIT");

        assertThatThrownBy(() -> service.cancel(customer, ORDER_ID, cancelForm("不需要了"), "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.ORDER_ALREADY_FULFILLING));
        verify(salesOrderService, never()).cancelFromMall(any(), any());
    }

    @Test
    void reorderSetsTheHistoricalQuantityWithoutCopyingTheHistoricalPrice() {
        when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CONFIRMED", 1));
        when(itemDao.list(ORDER_ID)).thenReturn(List.of(item(11L, "2.5000")));
        when(catalogService.visibleProducts(eq(customer), any())).thenReturn(List.of(product(11L, "6.0000")));

        MallReorderVO result = service.reorder(customer, ORDER_ID);

        assertThat(result.getAddedCount()).isEqualTo(1);
        assertThat(result.getSkipped()).isEmpty();
        // 数量覆盖为历史下单量；单价不参与写入，结算时按当前客户价重新解析
        verify(cartService).setQuantity(customer, 11L, new BigDecimal("2.5000"));
    }

    @Test
    void reorderSkipsItemsThatAreNoLongerVisibleOrPriced() {
        when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CONFIRMED", 1));
        when(itemDao.list(ORDER_ID)).thenReturn(List.of(item(11L, "1.0000"), item(12L, "1.0000"), item(13L, "1.0000")));
        when(catalogService.visibleProducts(eq(customer), any()))
                .thenReturn(List.of(product(11L, "6.0000"), product(12L, null)));

        MallReorderVO result = service.reorder(customer, ORDER_ID);

        assertThat(result.getAddedCount()).isEqualTo(1);
        assertThat(result.getSkipped()).extracting(MallReorderVO.Skipped::getSkuId).containsExactly(12L, 13L);
        assertThat(result.getSkipped()).extracting(MallReorderVO.Skipped::getReason)
                .containsExactly("暂无客户价，请联系业务员询价", "商品已下架或对当前客户不可见");
        verify(cartService).setQuantity(customer, 11L, new BigDecimal("1.0000"));
        verify(cartService, never()).setQuantity(eq(customer), eq(12L), any());
        verify(cartService, never()).setQuantity(eq(customer), eq(13L), any());
    }

    private static RequestMallCustomer customer() {
        RequestMallCustomer result = new RequestMallCustomer();
        result.setAccountId(7L);
        result.setCustomerId(9L);
        return result;
    }

    private static SalesOrderEntity order(String status, int version) {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(ORDER_ID);
        order.setStatus(status);
        order.setVersion(version);
        return order;
    }

    private static SalesOrderItemEntity item(Long skuId, String quantity) {
        SalesOrderItemEntity item = new SalesOrderItemEntity();
        item.setSkuId(skuId);
        item.setProductNameSnapshot("商品" + skuId);
        item.setOrderedQuantity(new BigDecimal(quantity));
        return item;
    }

    private static MallProductVO product(Long skuId, String unitPrice) {
        MallProductVO product = new MallProductVO();
        product.setSkuId(skuId);
        product.setUnitPrice(unitPrice == null ? null : new BigDecimal(unitPrice));
        return product;
    }

    private static MallOrderCancelForm cancelForm(String reason) {
        MallOrderCancelForm form = new MallOrderCancelForm();
        form.setReason(reason);
        return form;
    }
}
