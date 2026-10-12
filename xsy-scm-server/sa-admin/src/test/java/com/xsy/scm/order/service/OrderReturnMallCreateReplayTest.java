package com.xsy.scm.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.service.FinanceReceivableService;
import com.xsy.scm.order.dao.OrderRefundDao;
import com.xsy.scm.order.dao.OrderReturnDao;
import com.xsy.scm.order.dao.OrderReturnItemDao;
import com.xsy.scm.order.dao.OrderReturnReceiptItemDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import org.junit.jupiter.api.Test;

class OrderReturnMallCreateReplayTest {
    @Test
    void replayReturnsTheOriginalReturnBeforeAnotherOrderOrReturnWrite() {
        OrderIdempotencyService idempotency = mock(OrderIdempotencyService.class);
        SalesOrderService salesOrders = mock(SalesOrderService.class);
        OrderReturnDao returns = mock(OrderReturnDao.class);
        OrderReturnService service = new OrderReturnService(salesOrders, mock(SalesOrderDao.class),
                mock(SalesOrderItemDao.class), returns, mock(OrderReturnItemDao.class),
                mock(OrderReturnReceiptItemDao.class), mock(OrderRefundDao.class), mock(OrderNumberGenerator.class),
                idempotency, mock(OrderOperationLogRecorder.class), mock(ScmDataScopeService.class),
                mock(FinanceReceivableService.class));
        OrderReturnAddForm request = new OrderReturnAddForm();
        request.setOrderId(21L);
        OrderIdempotencyService.Claim claim = new OrderIdempotencyService.Claim(null, true);
        OrderReturnDetailVO original = new OrderReturnDetailVO();
        original.setReturnId(33L);
        when(idempotency.claim("ORDER_RETURN_CREATE", "same-key", request)).thenReturn(claim);
        when(idempotency.replay(claim, OrderReturnDetailVO.class)).thenReturn(original);

        assertThat(service.createFromMall(request, "same-key")).isSameAs(original);
        verify(idempotency).claim("ORDER_RETURN_CREATE", "same-key", request);
        verifyNoInteractions(salesOrders, returns);
    }
}
