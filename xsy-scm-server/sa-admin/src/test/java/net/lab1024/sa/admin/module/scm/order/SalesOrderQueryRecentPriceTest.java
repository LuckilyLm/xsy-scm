package net.lab1024.sa.admin.module.scm.order;

import net.lab1024.sa.admin.module.scm.order.dao.OrderAddressSnapshotDao;
import net.lab1024.sa.admin.module.scm.order.dao.OrderOperationLogDao;
import net.lab1024.sa.admin.module.scm.order.dao.SalesOrderDao;
import net.lab1024.sa.admin.module.scm.order.dao.SalesOrderItemDao;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderRecentPriceVO;
import net.lab1024.sa.admin.module.scm.order.service.SalesOrderQueryService;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave 3 §7.5：最近成交价的 limit 裁剪是纯服务层不变量（[1,10]，越界收敛而非报错），
 * 与 SQL 无关，用轻量 Mockito 直接验证传给 DAO 的实参，无需数据库。
 */
class SalesOrderQueryRecentPriceTest {

    private SalesOrderQueryService serviceWith(SalesOrderItemDao items) {
        return new SalesOrderQueryService(mock(SalesOrderDao.class), items, mock(OrderAddressSnapshotDao.class),
                mock(OrderOperationLogDao.class), mock(EmployeeDao.class));
    }

    @Test
    void clampsLimitIntoRangeBeforeHittingDao() {
        var items = mock(SalesOrderItemDao.class);
        when(items.recentPrices(anyLong(), anyLong(), anyInt())).thenReturn(List.<OrderRecentPriceVO>of());
        var service = serviceWith(items);

        service.recentPrices(7L, 11L, 0);
        verify(items).recentPrices(7L, 11L, 1);

        service.recentPrices(7L, 12L, 999);
        verify(items).recentPrices(7L, 12L, 10);

        service.recentPrices(7L, 13L, 5);
        verify(items).recentPrices(7L, 13L, 5);

        service.recentPrices(7L, 14L, -3);
        verify(items).recentPrices(7L, 14L, 1);
    }
}
