package com.xsy.scm.metrics.dao;

import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.metrics.domain.InventoryHealthRow;
import com.xsy.scm.metrics.domain.PurchaseFilter;
import com.xsy.scm.metrics.domain.RankItem;
import com.xsy.scm.metrics.domain.SalesFilter;
import com.xsy.scm.metrics.domain.TrendPoint;
import com.xsy.scm.metrics.domain.TrendSeriesSelection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 经营指标 SQL 只负责汇总真实业务事实，统计日期按北京时间对齐。 */
@Mapper
public interface ScmBusinessMetricsDao {

    // ---------- 销售（确认轴） ----------

    /** 区间内确认的订单数。时间轴 {@code confirmed_at}。 */
    Long countOrdersByConfirmedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("filter") SalesFilter filter,
            @Param("scope") ScmDataScopeContext scope);

    /** 区间内确认订单的结算金额，即「销售额」。时间轴 {@code confirmed_at}。 */
    BigDecimal sumSettlementAmountByConfirmedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("filter") SalesFilter filter,
            @Param("scope") ScmDataScopeContext scope);

    /** 区间内创建的确认订单金额，即「下单金额」。时间轴 {@code created_at}，与销售额<b>不是同一个数</b>。 */
    BigDecimal sumOrderedAmountByCreatedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("scope") ScmDataScopeContext scope);

    /** 累计确认订单数（无时间轴）。 */
    Long countTotalOrders(@Param("scope") ScmDataScopeContext scope);

    /** 累计确认订单结算金额（无时间轴）。 */
    BigDecimal sumTotalSettlementAmount(@Param("scope") ScmDataScopeContext scope);

    /** 区间内成交客户数（有确认订单的客户去重）。时间轴 {@code confirmed_at}，与销售额同轴。 */
    Long countCustomersWithOrdersByConfirmedAt(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("filter") SalesFilter filter,
            @Param("scope") ScmDataScopeContext scope);

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

    /** 出入库数量按流水类型对应的方向集合查询。方向类型由库存枚举提供，SQL 不复制另一份类型清单。 */
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

    // ---------- 采购（提交轴 + 已提交状态） ----------

    /**
     * 区间内已提交的采购单数。时间轴 {@code submitted_at}。
     *
     * <p>
     * 状态清单由调用方从 {@code ScmPurchaseStatusEnum.committedNames()} 传入，<b>不在 SQL 里硬编码</b> —— 抄一份状态数组的副本，新增状态时就会静默少算。
     */
    Long countPurchaseOrdersBySubmittedAt(@Param("committedStatuses") List<String> committedStatuses,
            @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime,
            @Param("filter") PurchaseFilter filter, @Param("scope") ScmDataScopeContext scope);

    /** 区间内已提交采购单的金额，即「今日采购额」。时间轴 {@code submitted_at}。 */
    BigDecimal sumPurchaseAmountBySubmittedAt(@Param("committedStatuses") List<String> committedStatuses,
            @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime,
            @Param("filter") PurchaseFilter filter, @Param("scope") ScmDataScopeContext scope);

    /** 累计已提交采购单数（无时间轴）。 */
    Long countTotalPurchaseOrders(@Param("committedStatuses") List<String> committedStatuses,
            @Param("scope") ScmDataScopeContext scope);

    /** 累计已提交采购单金额（无时间轴）。 */
    BigDecimal sumTotalPurchaseAmount(@Param("committedStatuses") List<String> committedStatuses,
            @Param("scope") ScmDataScopeContext scope);

    /**
     * 收货单数。<b>确认轴 + {@code status = 'CONFIRMED'}</b>：草稿收货单只是还没提交的草稿， 与报表的「已确认收货单数」同一个口径。
     */
    Long countReceipts(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime,
            @Param("scope") ScmDataScopeContext scope);

    /** 区间内有已提交采购单的供应商去重数。时间轴与状态与采购单一致。 */
    Long countSuppliersWithOrdersBySubmittedAt(@Param("committedStatuses") List<String> committedStatuses,
            @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime,
            @Param("scope") ScmDataScopeContext scope);

    // ---------- 趋势 ----------

    /** 趋势按北京时间每天汇总，销售按确认日、采购按提交日统计；各序列需与对应今日指标同口径。 */
    List<TrendPoint> trendByDay(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("inboundTypes") List<String> inboundTypes, @Param("outboundTypes") List<String> outboundTypes,
            @Param("committedStatuses") List<String> committedStatuses, @Param("scope") ScmDataScopeContext scope);

    List<TrendPoint> selectedTrendByDay(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("inboundTypes") List<String> inboundTypes, @Param("outboundTypes") List<String> outboundTypes,
            @Param("committedStatuses") List<String> committedStatuses, @Param("scope") ScmDataScopeContext scope,
            @Param("selection") TrendSeriesSelection selection);
}
