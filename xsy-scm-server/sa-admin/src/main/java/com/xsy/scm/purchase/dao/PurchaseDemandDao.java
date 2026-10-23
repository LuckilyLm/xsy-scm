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
 * 采购需求的数据读写。生成需求只取已确认订单的实际数量，并按稳定顺序查询；多条需求按 ID 排序加锁， 创建时用数据库唯一约束防止重复插入。
 */
@Mapper
public interface PurchaseDemandDao extends BaseMapper<PurchaseDemandEntity> {

    /**
     * 只取已确认且有实际数量的销售订单行，按稳定顺序查询，保证重复生成结果一致。 订单行没有仓库字段，入库仓库的授权由后续采购和仓库校验负责。
     */
    List<SalesOrderItemEntity> listSourceItems(@Param("startAt") OffsetDateTime startAt,
        @Param("endAt") OffsetDateTime endAt, @Param("scope") ScmValueScope scope);

    /**
     * 分页查询（联 supplier / warehouse 取名称）。
     *
     * <p>
     * {@code scope} 是采购员维度的授权范围（{@code xsy_purchase_demand.purchaser_id}），与 {@code PurchaseOrderDao.query}
     * 同一规则；{@code null} 在 Mapper 里按拒绝处理，不表示「全部」。
     */
    List<PurchaseDemandVO> query(Page<?> page, @Param("query") PurchaseDemandQueryForm query,
        @Param("scope") ScmValueScope scope);

    /**
     * 按已确认订单的实际数量汇总需求，并与指定仓库的余额比较。缺口与状态由 SQL 计算； 仓库范围限制在请求仓库，聚合分页按组数统计。
     */
    List<PurchaseDemandSummaryVO> summaryPreview(Page<?> page, @Param("query") PurchaseDemandSummaryPreviewForm query,
        @Param("scope") ScmValueScope scope);

    /**
     * 单条详情（同一套查询，保证列表与详情字段一致）。
     */
    PurchaseDemandVO detail(@Param("id") Long id);

    /**
     * 按 id 升序逐个 {@code FOR UPDATE} 锁定需求，保证固定的加锁顺序。
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
