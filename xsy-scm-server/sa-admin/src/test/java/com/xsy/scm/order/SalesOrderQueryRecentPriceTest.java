package com.xsy.scm.order;

import com.xsy.scm.order.dao.OrderAddressSnapshotDao;
import com.xsy.scm.order.dao.OrderOperationLogDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.vo.OrderRecentPriceVO;
import com.xsy.scm.mall.account.dao.MallCustomerAccountDao;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.common.json.ScmOperatorNameResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 最近成交价的 limit 裁剪是纯服务层不变量（[1,10]，越界收敛而非报错），
 * 与 SQL 无关，用轻量 Mockito 直接验证传给 DAO 的实参，无需数据库。
 */
class SalesOrderQueryRecentPriceTest {

    private SalesOrderQueryService serviceWith(SalesOrderItemDao items) {
        var scopes = mock(com.xsy.scm.common.scope.ScmDataScopeService.class);
        var context = mock(com.xsy.scm.common.scope.ScmDataScopeContext.class);
        when(scopes.resolve()).thenReturn(context);
        when(context.getOrderSellerScope()).thenReturn(com.xsy.scm.common.scope.ScmValueScope.all());
        return new SalesOrderQueryService(mock(SalesOrderDao.class), items, mock(com.xsy.scm.order.dao.OrderTagDao.class), mock(OrderAddressSnapshotDao.class),
                mock(OrderOperationLogDao.class), mock(ScmOperatorNameResolver.class), mock(MallCustomerAccountDao.class),
                scopes,
                mock(CustomerService.class),
                mock(com.xsy.scm.purchase.dao.PurchaseOrderItemDao.class),
                mock(com.xsy.scm.promotion.service.PromotionDiscountService.class));
    }

    @Test
    void clampsLimitIntoRangeBeforeHittingDao() {
        var items = mock(SalesOrderItemDao.class);
        when(items.recentPrices(anyLong(), anyLong(), anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(List.<OrderRecentPriceVO>of());
        var service = serviceWith(items);

        service.recentPrices(7L, 11L, 0);
        verify(items).recentPrices(7L, 11L, 1, com.xsy.scm.common.scope.ScmValueScope.all());

        service.recentPrices(7L, 12L, 999);
        verify(items).recentPrices(7L, 12L, 10, com.xsy.scm.common.scope.ScmValueScope.all());

        service.recentPrices(7L, 13L, 5);
        verify(items).recentPrices(7L, 13L, 5, com.xsy.scm.common.scope.ScmValueScope.all());

        service.recentPrices(7L, 14L, -3);
        verify(items).recentPrices(7L, 14L, 1, com.xsy.scm.common.scope.ScmValueScope.all());
    }
}
