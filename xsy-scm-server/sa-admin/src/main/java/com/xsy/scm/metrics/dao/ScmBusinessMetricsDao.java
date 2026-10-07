package com.xsy.scm.metrics.dao;

import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.metrics.domain.InventoryHealthRow;
import com.xsy.scm.metrics.domain.RankItem;
import com.xsy.scm.metrics.domain.TrendPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 经营类指标的只读聚合 DAO，是首页 / 大屏 / 报表共用的唯一取数处。
 *
 * <p>
 * <b>方法名必须写出时间轴</b>（{@code ByConfirmedAt} / {@code ByCreatedAt}）：同一个「销售金额」在确认轴与创建轴上是两个数，
 * 名字里不写清楚，调用方就会拿错。同一个指标不在这里出现第二个方法 —— 要加口径就加名字，不要加实现。
 *
 * <p>
 * <b>{@code scope} 是必填参数</b>：聚合值比明细更容易被误当成「已经授权过的数据」。上下文由调用方每个请求解析一次再下传，
 * 不要在这里解析。只有确实带仓库列或业务归属列的语句收该参数；{@link #countSuppliers()} 与 {@link #countSkus()} 两张主档 既无仓库列也无归属列（采购归属在
 * {@code supplier_sku} 上），按裁决属团队共享读，不收窄。
 */
@Mapper
public interface ScmBusinessMetricsDao {

    // ---------- 销售（确认轴） ----------

    /** 区间内确认的订单数。时间轴 {@code confirmed_at}。 */
    Long countOrdersByConfirmedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("scope") ScmDataScopeContext scope);

    /** 区间内确认订单的结算金额，即「销售额」。时间轴 {@code confirmed_at}。 */
    BigDecimal sumSettlementAmountByConfirmedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("scope") ScmDataScopeContext scope);

    /** 区间内创建的确认订单金额，即「下单金额」。时间轴 {@code created_at}，与销售额<b>不是同一个数</b>。 */
    BigDecimal sumOrderedAmountByCreatedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("scope") ScmDataScopeContext scope);

    /** 累计确认订单数（无时间轴）。 */
    Long countTotalOrders(@Param("scope") ScmDataScopeContext scope);

    /** 累计确认订单结算金额（无时间轴）。 */
    BigDecimal sumTotalSettlementAmount(@Param("scope") ScmDataScopeContext scope);

    /** 区间内成交客户数（有确认订单的客户去重）。时间轴 {@code confirmed_at}，与销售额同轴。 */
    Long countCustomersWithOrdersByConfirmedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("scope") ScmDataScopeContext scope);

    /** 客户销售排行。时间轴 {@code confirmed_at}，与销售额同轴。 */
    List<RankItem> topCustomersByConfirmedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("limit") int limit,
            @Param("scope") ScmDataScopeContext scope);

    /**
     * 商品销售排行。时间轴 {@code confirmed_at}，与销售额同轴。
     *
     * <p>
     * 按 SPU 归并而不是按 SKU：一个 SPU 下多个规格会让同一个商品名在榜单里出现两次，看起来像数据重复。
     */
    List<RankItem> topProductsByConfirmedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("limit") int limit,
            @Param("scope") ScmDataScopeContext scope);

    // ---------- 主档 ----------

    Long countCustomers(@Param("scope") ScmDataScopeContext scope);

    Long countSuppliers();

    Long countSkus();

    // ---------- 库存 ----------

    BigDecimal sumInventoryQuantity(@Param("scope") ScmDataScopeContext scope);

    Long countInventorySkus(@Param("scope") ScmDataScopeContext scope);

    Long countEnabledWarehouses(@Param("scope") ScmDataScopeContext scope);

    /**
     * 按流水类型集合统计条数。
     *
     * <p>
     * 接收集合而不是单个类型：调用方传的是<b>方向集合</b>（入库方向 / 出库方向），由 {@code ScmInventoryMovementTypeEnum} 的方向位派生 —— 不要在 SQL
     * 里抄一份类型清单，新增流水类型时那种副本会静默少算。
     */
    Long countMovementsByTypeAndRange(@Param("movementTypes") List<String> movementTypes,
            @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime,
            @Param("scope") ScmDataScopeContext scope);

    /**
     * 库存健康度判定输入行：只取事实，不在这里分类（分类见 {@code ScmInventoryWarningStatusEnum#evaluate}）。
     *
     * <p>
     * 返回「有阈值的 (仓库, 规格)」∪「有余额但无阈值的 (仓库, 规格)」：前者以阈值配置为准（左连余额，可能没有余额行）， 后者只有余额没有判定依据。
     */
    List<InventoryHealthRow> inventoryHealthRows(@Param("scope") ScmDataScopeContext scope);

    // ---------- 采购（创建轴，不过滤状态） ----------

    Long countPurchaseOrdersByCreatedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("scope") ScmDataScopeContext scope);

    BigDecimal sumPurchaseAmountByCreatedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("scope") ScmDataScopeContext scope);

    Long countTotalPurchaseOrders(@Param("scope") ScmDataScopeContext scope);

    BigDecimal sumTotalPurchaseAmount(@Param("scope") ScmDataScopeContext scope);

    /** 收货单数。{@code purchase_receipt} 有仓库列但没有采购员列，仓库是唯一可用的范围维度。 */
    Long countReceipts(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime,
            @Param("scope") ScmDataScopeContext scope);

    /** 区间内有采购单的供应商去重数。时间轴 {@code created_at}。 */
    Long countSuppliersWithOrdersByCreatedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("scope") ScmDataScopeContext scope);

    // ---------- 趋势 ----------

    /**
     * 按天聚合的趋势行：一天一行、八条序列。
     *
     * <p>
     * 日期口径一律 Asia/Shanghai。{@code sales} / {@code orders} 走确认轴，与今日销售额 / 今日订单同口径。 {@code inventoryQuantity}
     * 是当日期末库存量（当日及之前所有流水净额累加），不是当日变动量。
     */
    List<TrendPoint> trendByDay(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("inboundTypes") List<String> inboundTypes, @Param("outboundTypes") List<String> outboundTypes,
            @Param("scope") ScmDataScopeContext scope);
}
