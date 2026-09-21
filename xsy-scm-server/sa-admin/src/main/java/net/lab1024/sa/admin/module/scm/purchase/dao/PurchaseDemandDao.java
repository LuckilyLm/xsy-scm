package net.lab1024.sa.admin.module.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseDemandVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 采购需求（W5 Target Design §7.4 / §5.3）。
 *
 * <p>三个要点：
 * <ul>
 *   <li>{@link #listSourceItems} 是 `generate` 的**取数口径**，返回 W4 的 {@code SalesOrderItemEntity}
 *       （需求快照的 8 个商品字段全部来自 {@code sales_order_item}，无需自建投影类型）；
 *       订单号与确认时间由调用方用 {@code SalesOrderDao.selectBatchIds} 批量补齐（2 次查询，无 N+1）；</li>
 *   <li>{@link #lockByIds} 强制 {@code ORDER BY id ASC FOR UPDATE} —— **P12 锁序**，
 *       避免与 `order.create` 路径交叉成环；</li>
 *   <li>{@link #insertIgnore} 用 {@code ON CONFLICT DO NOTHING} 做 INSERT 竞争
 *       （不先查后插，修 A-D17），冲突时由调用方重读收敛到同一行。</li>
 * </ul>
 */
@Mapper
public interface PurchaseDemandDao extends BaseMapper<PurchaseDemandEntity> {

    /**
     * 来源行（`generate` 取数）：已确认订单 + 有实数量，确定性排序。
     *
     * <p>排序 `sku_id ASC, confirmed_at ASC, id ASC` 与 §7.4 第 3 步一致 ——
     * 确定性顺序让「同一窗口重复 generate」的结果可复现（幂等重放的前提）。
     */
    List<SalesOrderItemEntity> listSourceItems(@Param("startAt") OffsetDateTime startAt,
                                               @Param("endAt") OffsetDateTime endAt);

    /**
     * 分页查询（联 supplier / warehouse 取名称快照）。
     */
    List<PurchaseDemandVO> query(Page<?> page, @Param("query") PurchaseDemandQueryForm query);

    /**
     * 单条详情（同一套投影，保证列表与详情字段口径一致）。
     */
    PurchaseDemandVO detail(@Param("id") Long id);

    /**
     * 按 id 升序逐个 `FOR UPDATE` 锁定需求（**P12 锁序**）。
     *
     * <p>调用方必须先经 {@code PurchaseDemandAllocator.ascendingDemandIds} 去重排序，
     * 否则不同事务可能以不同顺序取锁而成环。
     */
    List<PurchaseDemandEntity> lockByIds(@Param("ids") List<Long> ids);

    /**
     * 单条 `FOR UPDATE`。
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
     * 重算分配（§7.8 C 段）。
     *
     * <p>同时更新 `allocated_quantity` / `status` / `supplier_id`（首次分配固定）与 `version + 1`；
     * 用 `version` 做乐观锁，影响行数为 0 表示并发冲突。
     */
    int updateAllocation(@Param("id") Long id,
                         @Param("version") Integer version,
                         @Param("allocatedQuantity") BigDecimal allocatedQuantity,
                         @Param("status") String status,
                         @Param("supplierId") Long supplierId,
                         @Param("operator") String operator);

    /**
     * 软删（需求回退用；仅在没有活动分配时允许）。
     */
    int softDelete(@Param("id") Long id,
                   @Param("version") Integer version,
                   @Param("operator") String operator);
}
