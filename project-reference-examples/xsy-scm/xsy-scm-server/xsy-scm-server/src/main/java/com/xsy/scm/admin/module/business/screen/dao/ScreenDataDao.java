package com.xsy.scm.admin.module.business.screen.dao;

import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenBusinessVO;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenPurchaseVO;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenStockVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 大屏数据聚合 Dao（只读）
 *
 * @author xsy-scm
 */
@Mapper
public interface ScreenDataDao {

    /**
     * 经营大屏：区间内订单量与销售额
     */
    ScreenBusinessVO selectBusinessOrder(@Param("startTime") java.time.LocalDateTime startTime,
                                         @Param("endTime") java.time.LocalDateTime endTime);

    /**
     * 客户数
     */
    Long countCustomer();

    /**
     * 待收余额合计
     */
    BigDecimal selectReceivableBalance();

    /**
     * 库存大屏：规格数、总数量、总成本
     */
    ScreenStockVO selectStock();

    /**
     * 库存预警数（低于下限）
     */
    Long countStockWarn();

    /**
     * 采购大屏：区间内采购单量与采购金额
     */
    ScreenPurchaseVO selectPurchase(@Param("startTime") java.time.LocalDateTime startTime,
                                    @Param("endTime") java.time.LocalDateTime endTime);

    /**
     * 供应商数
     */
    Long countSupplier();
}
