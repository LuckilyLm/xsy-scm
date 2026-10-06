package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandEntity;
import com.xsy.scm.purchase.domain.form.PurchaseDemandQueryForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandSummaryPreviewForm;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandSummaryVO;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 采购需求。
 *
 * <p>
 * 三个要点：
 * <ul>
 * <li>{@link #listSourceItems} 是 {@code generate} 的<b>取数口径</b>，返回 {@code SalesOrderItemEntity} （需求快照的 8 个商品字段全部来自
 * {@code sales_order_item}，无需自建投影类型）；订单号与确认时间由调用方用 {@code SalesOrderDao.selectBatchIds} 批量补齐（2 次查询，无 N+1）；</li>
 * <li>{@link #lockByIds} 按 id 升序加行锁，避免与订单写路径形成反向锁序；</li>
 * <li>{@link #insertIgnore} 用 {@code ON CONFLICT DO NOTHING} 做 INSERT 竞争 （不先查后插），冲突时由调用方重读收敛到同一行。</li>
 * </ul>
 */
@Mapper
public interface PurchaseDemandDao extends BaseMapper<PurchaseDemandEntity> {

    /**
     * 来源行（{@code generate} 取数）：已确认订单 + 有实数量，确定性排序。
     *
     * <p>
     * 按 SKU、订单确认时间与行 id 排序，使同一窗口的重复 generate 得到相同结果。
     *
     * <p>
     * {@code scope} 是仓库维度的授权范围，但本口径取的是 {@code sales_order} 与 {@code sales_order_item}，两张表都没有仓库列（仓库在需求/库存侧才出现），因此按仓库不收窄；
     * 命令路径必须显式传 {@link ScmValueScope#all()}（见 {@code PurchaseDemandService#generate}）， {@code null}
     * 仍失败关闭。要收的是这条命令写入哪个仓，那道边界在 {@code PurchaseWarehouseReferenceGuard} 与后续收货/出库的仓库守卫上。
     */
    List<SalesOrderItemEntity> listSourceItems(@Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("scope") ScmValueScope scope);

    /**
     * 分页查询（联 supplier / warehouse 取名称快照）。
     *
     * <p>
     * {@code scope} 是采购员维度的授权范围（{@code purchase_demand.purchaser_id}），与 {@code PurchaseOrderDao.query}
     * 同一口径；{@code null} 在 Mapper 里失败关闭，不表示「全部」。
     */
    List<PurchaseDemandVO> query(Page<?> page, @Param("query") PurchaseDemandQueryForm query,
            @Param("scope") ScmValueScope scope);

    /**
     * 订单汇总 / 库存缺口预览（只读聚合）。
     *
     * <p>
     * WHERE 与 {@link #listSourceItems} 同源（已确认订单 + 有实数量 + 同一确认窗口），按 {@code sku_id + sale_unit_snapshot} 聚合后左连
     * {@code inventory_balance} （{@code warehouse_id} 来自入参）比对可用量。可用量、缺口、状态全在 SQL 里用 {@code NUMERIC} 算好，Java/前端不做浮点运算。
     *
     * <p>
     * {@code scope} 是调用者的<b>仓库</b>授权范围：整页数字都归属到入参那一个仓库 （余额、预留、由它算出的缺口），因此范围谓词落在「请求仓」上而不是余额左连上，详见 XML 里的说明。{@code null}
     * 失败关闭。
     *
     * <p>
     * 调用方须关闭 count SQL 优化（GROUP BY 分页），否则自动 count 会按行数而非组数计数。
     */
    List<PurchaseDemandSummaryVO> summaryPreview(Page<?> page, @Param("query") PurchaseDemandSummaryPreviewForm query,
            @Param("scope") ScmValueScope scope);

    /**
     * 单条详情（同一套投影，保证列表与详情字段口径一致）。
     */
    PurchaseDemandVO detail(@Param("id") Long id);

    /**
     * 按 id 升序逐个 {@code FOR UPDATE} 锁定需求（<b>锁序</b>）。
     *
     * <p>
     * 调用方必须先经 {@code PurchaseDemandAllocator.ascendingDemandIds} 去重排序，否则不同事务可能以不同顺序取锁而成环。
     */
    List<PurchaseDemandEntity> lockByIds(@Param("ids") List<Long> ids);

    /**
     * 单条 {@code FOR UPDATE}。
     */
    PurchaseDemandEntity lock(@Param("id") Long id);

    /**
     * 按来源销售订单行查活动需求（去重与「已存在则返回已有 id」用）。
     */
    List<PurchaseDemandEntity> listActiveBySourceItemIds(@Param("ids") List<Long> ids);

    /**
     * INSERT 竞争：冲突（唯一索引）时返回 0，由调用方重读。不返回自增主键。
     */
    int insertIgnore(@Param("row") PurchaseDemandEntity row);

    /**
     * 重算分配。
     *
     * <p>
     * 同时更新 {@code allocated_quantity} / {@code status} / {@code supplier_id}（首次分配固定）与 {@code version + 1}；用
     * {@code version} 做乐观锁，影响行数为 0 表示并发冲突。
     */
    int updateAllocation(@Param("id") Long id, @Param("version") Integer version,
            @Param("allocatedQuantity") BigDecimal allocatedQuantity, @Param("status") String status,
            @Param("supplierId") Long supplierId, @Param("operator") String operator);

    /**
     * 软删（需求回退用；仅在没有活动分配时允许）。
     */
    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);
}
