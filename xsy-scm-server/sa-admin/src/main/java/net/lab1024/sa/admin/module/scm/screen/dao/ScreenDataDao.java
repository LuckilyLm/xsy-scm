package net.lab1024.sa.admin.module.scm.screen.dao;

import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenBusinessVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenGeoVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryHealthRow;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenTrendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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

    /**
     * 按流水类型集合统计条数。
     *
     * <p>接收集合而不是单个类型：调用方传的是**方向集合**（入库方向 / 出库方向），
     * 由 {@link net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum}
     * 的方向位派生 —— 不要在 SQL 里抄一份类型清单，新增流水类型时那种副本会静默少算。
     */
    Long countMovementsByTypeAndRange(@Param("movementTypes") List<String> movementTypes,
                                      @Param("startTime") OffsetDateTime startTime,
                                      @Param("endTime") OffsetDateTime endTime);

    List<ScreenInventoryVO.WarehouseDistribution> inventoryDistributionByWarehouse();

    /**
     * 库存健康度判定输入行。
     *
     * <p>返回的集合是「有阈值的 (仓库, SKU)」∪「有余额但无阈值的 (仓库, SKU)」——
     * 前者以阈值配置为准（左连余额，可能没有余额行），后者只有余额没有判定依据。
     * **不在这里分类**，分类由 Java 侧调用预警枚举完成（见 {@link ScreenInventoryHealthRow}）。
     */
    List<ScreenInventoryHealthRow> inventoryHealthRows();

    /** 供应链网络节点：仅启用仓库，带库存量与今日出库量（出库方向的全部流水类型）。 */
    List<ScreenInventoryVO.WarehouseNode> warehouseNetworkNodes(@Param("startTime") OffsetDateTime startTime,
                                                                @Param("endTime") OffsetDateTime endTime,
                                                                @Param("outboundTypes") List<String> outboundTypes);

    // ---------- 趋势 ----------

    /**
     * 按天聚合的趋势行。
     *
     * <p>日期轴由 {@code generate_series} 生成，**没有单据的日期也会返回一行（全为 0）**；
     * 日期口径是 Asia/Shanghai。一次返回全部 8 条序列，避免三张图的日期轴各自漂移。
     *
     * <p>{@code inboundTypes} / {@code outboundTypes} 由调用方从流水类型枚举的方向位派生，
     * **不在 SQL 里硬编码类型名**。
     */
    List<ScreenTrendVO.Point> trendByDay(@Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate,
                                         @Param("inboundTypes") List<String> inboundTypes,
                                         @Param("outboundTypes") List<String> outboundTypes);

    // ---------- 采购 ----------

    Long countPurchaseOrders(@Param("startTime") OffsetDateTime startTime,
                             @Param("endTime") OffsetDateTime endTime);

    java.math.BigDecimal sumPurchaseAmount(@Param("startTime") OffsetDateTime startTime,
                                           @Param("endTime") OffsetDateTime endTime);

    Long countTotalPurchaseOrders();

    java.math.BigDecimal sumTotalPurchaseAmount();

    Long countReceipts(@Param("startTime") OffsetDateTime startTime,
                       @Param("endTime") OffsetDateTime endTime);

    /** 今日成交客户数（有 CONFIRMED 订单的客户去重）。 */
    Long countCustomersWithOrdersInRange(@Param("startTime") OffsetDateTime startTime,
                                         @Param("endTime") OffsetDateTime endTime);

    /** 今日活跃供应商数（有采购单的供应商去重）。 */
    Long countSuppliersWithOrdersInRange(@Param("startTime") OffsetDateTime startTime,
                                         @Param("endTime") OffsetDateTime endTime);

    // ---------- 地理分布（地图 M1） ----------

    /**
     * 按市聚合的主档归属行，坐标取 {@code scm_region} 的区划质心。
     *
     * <p>只返回**已解析出市级归属、且编码能在区划字典里查到**的行；省级分布由调用方在这里
     * 的结果上向上卷一层，避免两份 SQL 各自演算导致省界与气泡对不上。
     */
    List<ScreenGeoVO.CityNode> geoCityRows();

    /** 三张主档各自的「总数 / 已归属数」，与 {@link #geoCityRows()} 共用同一份口径片段。 */
    ScreenGeoVO.Coverage geoCoverage();
}
