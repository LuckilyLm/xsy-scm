package com.xsy.scm.admin.module.business.screen.service;

import com.xsy.scm.admin.module.business.screen.dao.ScreenDataDao;
import com.xsy.scm.admin.module.business.screen.domain.form.ScreenDataQueryForm;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenBusinessVO;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenPurchaseVO;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenStockVO;
import com.xsy.scm.base.common.domain.ResponseDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 大屏数据 Service（只读聚合，不写业务表）
 *
 * <p>统计口径与财务一致（不含税、金额到分）；startTime / endTime 为空时默认统计「今日」。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ScreenDataService {

    @Resource
    private ScreenDataDao screenDataDao;

    /**
     * 经营大屏：订单量、销售额、待收余额、客户数
     */
    public ResponseDTO<ScreenBusinessVO> business(ScreenDataQueryForm queryForm) {
        LocalDateTime[] range = resolveRange(queryForm);
        ScreenBusinessVO vo = screenDataDao.selectBusinessOrder(range[0], range[1]);
        if (vo == null) {
            vo = new ScreenBusinessVO();
        }
        if (vo.getOrderCount() == null) {
            vo.setOrderCount(0L);
        }
        vo.setSalesAmount(nullToZero(vo.getSalesAmount()));
        vo.setCustomerCount(nullToLong(screenDataDao.countCustomer()));
        vo.setReceivableBalance(nullToZero(screenDataDao.selectReceivableBalance()));
        return ResponseDTO.ok(vo);
    }

    /**
     * 库存大屏：规格数、总数量、总成本、预警数
     */
    public ResponseDTO<ScreenStockVO> stock() {
        ScreenStockVO vo = screenDataDao.selectStock();
        if (vo == null) {
            vo = new ScreenStockVO();
        }
        if (vo.getProductCount() == null) {
            vo.setProductCount(0L);
        }
        vo.setTotalQuantity(nullToZero(vo.getTotalQuantity()));
        vo.setTotalCost(nullToZero(vo.getTotalCost()));
        vo.setWarnCount(nullToLong(screenDataDao.countStockWarn()));
        return ResponseDTO.ok(vo);
    }

    /**
     * 采购大屏：采购单量、采购金额、供应商数
     */
    public ResponseDTO<ScreenPurchaseVO> purchase(ScreenDataQueryForm queryForm) {
        LocalDateTime[] range = resolveRange(queryForm);
        ScreenPurchaseVO vo = screenDataDao.selectPurchase(range[0], range[1]);
        if (vo == null) {
            vo = new ScreenPurchaseVO();
        }
        if (vo.getPurchaseCount() == null) {
            vo.setPurchaseCount(0L);
        }
        vo.setPurchaseAmount(nullToZero(vo.getPurchaseAmount()));
        vo.setSupplierCount(nullToLong(screenDataDao.countSupplier()));
        return ResponseDTO.ok(vo);
    }

    /**
     * 解析统计区间：缺省为「今日」
     */
    private LocalDateTime[] resolveRange(ScreenDataQueryForm queryForm) {
        LocalDateTime start = queryForm.getStartTime() != null
                ? queryForm.getStartTime() : LocalDate.now().atStartOfDay();
        LocalDateTime end = queryForm.getEndTime() != null
                ? queryForm.getEndTime() : start.plusDays(1);
        return new LocalDateTime[]{start, end};
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long nullToLong(Long value) {
        return value == null ? 0L : value;
    }
}
