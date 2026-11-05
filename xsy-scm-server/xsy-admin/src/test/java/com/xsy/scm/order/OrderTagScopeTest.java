package com.xsy.scm.order;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.dao.OrderTagDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.form.OrderTagAssignForm;
import com.xsy.scm.order.service.OrderTagService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderTagScopeTest {
    @Test
    void bulkClearChecksAllOwnersBeforeWritingAnyTag() {
        var dao = mock(OrderTagDao.class);
        var orders = mock(SalesOrderDao.class);
        var scopes = mock(ScmDataScopeService.class);
        var context = mock(ScmDataScopeContext.class);
        when(scopes.resolve()).thenReturn(context);
        when(context.getOrderSellerScope()).thenReturn(ScmValueScope.of(List.of(10L)));
        var own = new SalesOrderEntity();
        own.setSellerId(10L);
        var other = new SalesOrderEntity();
        other.setSellerId(20L);
        when(orders.lock(1L)).thenReturn(own);
        when(orders.lock(2L)).thenReturn(other);
        var form = new OrderTagAssignForm();
        form.setOrderIds(List.of(2L, 1L));
        form.setAction(OrderTagAssignForm.Action.CLEAR);
        try (var operator = mockStatic(ScmOperator.class)) {
            operator.when(ScmOperator::current).thenReturn("1:1");
            assertThatThrownBy(() -> new OrderTagService(dao, orders, scopes).apply(form)).isInstanceOf(ScmDataScopeException.class);
        }
        verifyNoInteractions(dao);
    }
}
