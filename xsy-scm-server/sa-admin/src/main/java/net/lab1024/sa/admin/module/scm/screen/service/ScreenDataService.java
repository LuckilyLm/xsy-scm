package net.lab1024.sa.admin.module.scm.screen.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.screen.dao.ScreenDataDao;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenBusinessVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenPurchaseVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 数据大屏只读聚合服务。
 *
 * <p>只查询，不写业务表；所有统计基于现有业务域，不维护独立副本。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenDataService {

    private static final int TOP_RANK_LIMIT = 10;

    private final ScreenDataDao screenDataDao;

    /** 今日起止（UTC 口径，与数据库 TIMESTAMPTZ 对齐）。 */
    private OffsetDateTime[] todayRange() {
        LocalDate today = LocalDate.now();
        OffsetDateTime start = today.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = today.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return new OffsetDateTime[]{start, end};
    }

    public ScreenBusinessVO getBusinessData() {
        OffsetDateTime[] range = todayRange();
        ScreenBusinessVO vo = new ScreenBusinessVO();
        vo.setTodayOrderCount(nullToZero(screenDataDao.countConfirmedOrders(range[0], range[1])));
        vo.setTodayOrderedAmount(nullToZero(screenDataDao.sumOrderedAmount(range[0], range[1])));
        vo.setTodaySettlementAmount(nullToZero(screenDataDao.sumSettlementAmount(range[0], range[1])));
        vo.setTotalOrderCount(nullToZero(screenDataDao.countTotalConfirmedOrders()));
        vo.setTotalSettlementAmount(nullToZero(screenDataDao.sumTotalSettlementAmount()));
        vo.setCustomerCount(nullToZero(screenDataDao.countCustomers()));
        vo.setSupplierCount(nullToZero(screenDataDao.countSuppliers()));
        vo.setSkuCount(nullToZero(screenDataDao.countSkus()));
        vo.setTopCustomers(nullToEmpty(screenDataDao.topCustomersBySettlement(range[0], range[1], TOP_RANK_LIMIT)));
        vo.setTopProducts(nullToEmpty(screenDataDao.topProductsBySettlement(range[0], range[1], TOP_RANK_LIMIT)));
        return vo;
    }

    public ScreenInventoryVO getInventoryData() {
        OffsetDateTime[] range = todayRange();
        ScreenInventoryVO vo = new ScreenInventoryVO();
        vo.setTotalQuantity(nullToZero(screenDataDao.sumInventoryQuantity()));
        vo.setSkuCount(nullToZero(screenDataDao.countInventorySkus()));
        vo.setWarehouseCount(nullToZero(screenDataDao.countEnabledWarehouses()));
        vo.setTodayInboundCount(nullToZero(screenDataDao.countMovementsByTypeAndRange("PURCHASE_IN", range[0], range[1])));
        vo.setTodayOutboundCount(nullToZero(screenDataDao.countMovementsByTypeAndRange("SALES_OUT", range[0], range[1])));
        vo.setWarehouseDistribution(nullToEmpty(screenDataDao.inventoryDistributionByWarehouse()));
        return vo;
    }

    public ScreenPurchaseVO getPurchaseData() {
        OffsetDateTime[] range = todayRange();
        ScreenPurchaseVO vo = new ScreenPurchaseVO();
        vo.setTodayPurchaseOrderCount(nullToZero(screenDataDao.countPurchaseOrders(range[0], range[1])));
        vo.setTodayPurchaseAmount(nullToZero(screenDataDao.sumPurchaseAmount(range[0], range[1])));
        vo.setTotalPurchaseOrderCount(nullToZero(screenDataDao.countTotalPurchaseOrders()));
        vo.setTotalPurchaseAmount(nullToZero(screenDataDao.sumTotalPurchaseAmount()));
        vo.setTodayReceiptCount(nullToZero(screenDataDao.countReceipts(range[0], range[1])));
        return vo;
    }

    private static Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static <T> List<T> nullToEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }
}
