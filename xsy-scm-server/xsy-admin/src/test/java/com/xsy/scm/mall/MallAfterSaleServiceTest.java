package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.aftersale.domain.form.MallAfterSaleApplyForm;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleVO;
import com.xsy.scm.mall.aftersale.service.MallAfterSaleQueryService;
import com.xsy.scm.mall.aftersale.service.MallAfterSaleService;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.order.domain.entity.OrderReturnEntity;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnDecisionForm;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.service.OrderReturnService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 商城售后写操作的边界：申请与撤销都必须走正式退货路径，归属先于一切，状态门槛不由商城放宽。
 */
class MallAfterSaleServiceTest {
    private static final Long ORDER_ID = 21L;
    private static final Long RETURN_ID = 33L;

    private final MallOrderQueryService orderQueryService = mock(MallOrderQueryService.class);
    private final MallAfterSaleQueryService afterSaleQueryService = mock(MallAfterSaleQueryService.class);
    private final OrderReturnService orderReturnService = mock(OrderReturnService.class);
    private final MallAfterSaleService service = new MallAfterSaleService(orderQueryService, afterSaleQueryService,
            orderReturnService);
    private final RequestMallCustomer customer = customer();

    @Test
    void applyMapsLinesToTheOfficialReturnFormAfterOwnershipCheck() {
        OrderReturnDetailVO created = new OrderReturnDetailVO();
        created.setReturnId(RETURN_ID);
        when(orderReturnService.createFromMall(any(), eq("key"))).thenReturn(created);
        MallAfterSaleVO projected = new MallAfterSaleVO();
        when(afterSaleQueryService.detail(customer, RETURN_ID)).thenReturn(projected);

        MallAfterSaleVO result = service.apply(customer, applyForm(), "key");

        assertThat(result).isSameAs(projected);
        verify(orderQueryService).requireOwnedOrder(customer, ORDER_ID);
        ArgumentCaptor<OrderReturnAddForm> captor = ArgumentCaptor.forClass(OrderReturnAddForm.class);
        verify(orderReturnService).createFromMall(captor.capture(), eq("key"));
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getReason()).isEqualTo("商品损坏");
        assertThat(captor.getValue().getItems()).hasSize(1);
        assertThat(captor.getValue().getItems().getFirst().getOrderItemId()).isEqualTo(101L);
        assertThat(captor.getValue().getItems().getFirst().getRequestedQuantity()).isEqualTo("1.5");
    }

    @Test
    void cancelPassesCurrentVersionAndFixedCustomerReason() {
        when(afterSaleQueryService.requireOwnedReturn(customer, RETURN_ID)).thenReturn(returnEntity("PENDING", 4));

        service.cancel(customer, RETURN_ID, "key");

        ArgumentCaptor<OrderReturnDecisionForm> captor = ArgumentCaptor.forClass(OrderReturnDecisionForm.class);
        verify(orderReturnService).cancelFromMall(captor.capture(), eq("key"));
        assertThat(captor.getValue().getReturnId()).isEqualTo(RETURN_ID);
        assertThat(captor.getValue().getVersion()).isEqualTo(4);
        assertThat(captor.getValue().getDecisionReason()).isEqualTo("客户自行撤销");
    }

    @Test
    void cancelledRequestStillReachesTheSameIdempotencyKeyAfterVersionAdvances() {
        when(afterSaleQueryService.requireOwnedReturn(customer, RETURN_ID))
                .thenReturn(returnEntity("PENDING", 4), returnEntity("CANCELLED", 5));
        MallAfterSaleVO projected = new MallAfterSaleVO();
        when(afterSaleQueryService.detail(customer, RETURN_ID)).thenReturn(projected);

        assertThat(service.cancel(customer, RETURN_ID, "same-key")).isSameAs(projected);
        assertThat(service.cancel(customer, RETURN_ID, "same-key")).isSameAs(projected);

        ArgumentCaptor<OrderReturnDecisionForm> captor = ArgumentCaptor.forClass(OrderReturnDecisionForm.class);
        verify(orderReturnService, times(2)).cancelFromMall(captor.capture(), eq("same-key"));
        assertThat(captor.getAllValues()).extracting(OrderReturnDecisionForm::getVersion)
                .containsExactly(4, 5);
    }

    @Test
    void reviewedReturnCannotBeCancelledByTheCustomer() {
        when(afterSaleQueryService.requireOwnedReturn(customer, RETURN_ID)).thenReturn(returnEntity("APPROVED", 1));

        assertThatThrownBy(() -> service.cancel(customer, RETURN_ID, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MallErrorCode.AFTERSALE_NOT_CANCELABLE));
        verify(orderReturnService, never()).cancelFromMall(any(), any());
    }

    @Test
    void returnOwnedByAnotherCustomerIsInvisible() {
        when(afterSaleQueryService.requireOwnedReturn(customer, RETURN_ID))
                .thenThrow(new ScmBusinessException(MallErrorCode.AFTERSALE_NOT_FOUND));

        assertThatThrownBy(() -> service.cancel(customer, RETURN_ID, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MallErrorCode.AFTERSALE_NOT_FOUND));
        verify(orderReturnService, never()).cancelFromMall(any(), any());
    }

    @Test
    void otherCustomersOrderCannotCreateAnAfterSaleRequest() {
        when(orderQueryService.requireOwnedOrder(customer, ORDER_ID))
                .thenThrow(new ScmBusinessException(MallErrorCode.ORDER_NOT_FOUND));

        assertThatThrownBy(() -> service.apply(customer, applyForm(), "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MallErrorCode.ORDER_NOT_FOUND));
        verify(orderReturnService, never()).createFromMall(any(), any());
    }

    private static RequestMallCustomer customer() {
        RequestMallCustomer result = new RequestMallCustomer();
        result.setAccountId(7L);
        result.setCustomerId(9L);
        return result;
    }

    private static MallAfterSaleApplyForm applyForm() {
        MallAfterSaleApplyForm form = new MallAfterSaleApplyForm();
        form.setOrderId(ORDER_ID);
        form.setReason("商品损坏");
        MallAfterSaleApplyForm.Item item = new MallAfterSaleApplyForm.Item();
        item.setOrderItemId(101L);
        item.setQuantity("1.5");
        form.setItems(java.util.List.of(item));
        return form;
    }

    private static OrderReturnEntity returnEntity(String status, int version) {
        OrderReturnEntity entity = new OrderReturnEntity();
        entity.setId(RETURN_ID);
        entity.setOrderId(ORDER_ID);
        entity.setCustomerId(9L);
        entity.setStatus(status);
        entity.setVersion(version);
        return entity;
    }
}
