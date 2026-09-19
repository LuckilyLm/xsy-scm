package net.lab1024.sa.admin.module.scm.screen.dao;

import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenBusinessVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 数据大屏只读聚合 DAO。
 *
 * <p>所有 SQL 均为只读聚合，不写业务表；空结果通过 COALESCE 兜底为零。
 */
@Mapper
public interface ScreenDataDao {

    // ---------- 经营 ----------

    Long countConfirmedOrders(@Param("startTime") OffsetDateTime startTime,
                              @Param("endTime") OffsetDateTime endTime);

    java.math.BigDecimal sumOrderedAmount(@Param("startTime") OffsetDateTime startTime,
                                          @Param("endTime") OffsetDateTime endTime);

    java.math.BigDecimal sumSettlementAmount(@Param("startTime") OffsetDateTime startTime,
                                             @Param("endTime") OffsetDateTime endTime);

    Long countTotalConfirmedOrders();

    java.math.BigDecimal sumTotalSettlementAmount();

    Long countCustomers();

    Long countSuppliers();

    Long countSkus();

    List<ScreenBusinessVO.RankItem> topCustomersBySettlement(@Param("startTime") OffsetDateTime startTime,
                                                             @Param("endTime") OffsetDateTime endTime,
                                                             @Param("limit") int limit);

    List<ScreenBusinessVO.RankItem> topProductsBySettlement(@Param("startTime") OffsetDateTime startTime,
                                                            @Param("endTime") OffsetDateTime endTime,
                                                            @Param("limit") int limit);

    // ---------- 库存 ----------

    java.math.BigDecimal sumInventoryQuantity();

    Long countInventorySkus();

    Long countEnabledWarehouses();

    Long countMovementsByTypeAndRange(@Param("movementType") String movementType,
                                      @Param("startTime") OffsetDateTime startTime,
                                      @Param("endTime") OffsetDateTime endTime);

    List<ScreenInventoryVO.WarehouseDistribution> inventoryDistributionByWarehouse();

    // ---------- 采购 ----------

    Long countPurchaseOrders(@Param("startTime") OffsetDateTime startTime,
                             @Param("endTime") OffsetDateTime endTime);

    java.math.BigDecimal sumPurchaseAmount(@Param("startTime") OffsetDateTime startTime,
                                           @Param("endTime") OffsetDateTime endTime);

    Long countTotalPurchaseOrders();

    java.math.BigDecimal sumTotalPurchaseAmount();

    Long countReceipts(@Param("startTime") OffsetDateTime startTime,
                       @Param("endTime") OffsetDateTime endTime);
}
