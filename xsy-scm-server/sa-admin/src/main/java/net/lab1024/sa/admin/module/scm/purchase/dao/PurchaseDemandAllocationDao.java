package net.lab1024.sa.admin.module.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购需求分配（W5 Target Design §5.4 / §7.8 B 段，**Q13**）。
 *
 * <p>allocation 身份 = {@code (purchase_order_item_id, purchase_demand_id)}，
 * 由 {@code uk_purchase_demand_allocation_source_active} 强制。
 *
 * <p>**这里没有「按 itemId 查单条 allocation」的方法** —— 那是 A 源的错误形状（A-D23）。
 * 所有查询都是**集合形态**，让调用方按 allocation 集合做差量对账。
 *
 * <p>{@link #listActiveByDemandIds} 返回的是**跨采购单的全部活动分配**：
 * 需求侧 `allocated_quantity` 是跨单累计值，只按本单算会漏掉其它采购单的贡献。
 */
@Mapper
public interface PurchaseDemandAllocationDao extends BaseMapper<PurchaseDemandAllocationEntity> {

    /**
     * 本采购单的全部活动分配（联 purchase_order_item 限定 orderId）。
     */
    List<PurchaseDemandAllocationEntity> listActiveByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 指定采购行的全部活动分配。
     */
    List<PurchaseDemandAllocationEntity> listActiveByOrderItemIds(@Param("ids") List<Long> ids);

    /**
     * 指定需求的全部活动分配（**跨采购单**，用于重算 allocated_quantity）。
     */
    List<PurchaseDemandAllocationEntity> listActiveByDemandIds(@Param("ids") List<Long> ids);

    /**
     * 按 (itemId, demandId) 集合形态读取，用于差量对账前的旧集合装载。
     */
    List<PurchaseDemandAllocationEntity> listActiveByOrderIdAndDemandIds(@Param("purchaseOrderId") Long purchaseOrderId,
                                                                         @Param("demandIds") List<Long> demandIds);

    /**
     * 只改数量（**保留的 allocation**，§7.8 B 段第 9 步：不重建行）。
     *
     * <p>**刻意不用 MP 的 `updateById`**：`@Version` 的乐观锁要求实体上的 `version` 非空，
     * 而差量对象是从「请求」构造出来的（请求不带 allocation 版本），一旦漏设 version，
     * MP 会**静默跳过**版本条件、把并发写变成覆盖写。这里把版本作为显式参数传给 SQL，
     * 影响行数为 0 就一定是并发冲突 —— 与 {@link #softDelete} 同一形状。
     */
    int updateQuantity(@Param("id") Long id,
                       @Param("version") Integer version,
                       @Param("allocatedQuantity") BigDecimal allocatedQuantity,
                       @Param("operator") String operator);

    /**
     * 软删单条（**只删这一条**，不波及同一行的其它分配）。
     */
    int softDelete(@Param("id") Long id,
                   @Param("version") Integer version,
                   @Param("operator") String operator);

    /**
     * 软删某个采购行的全部分配（行被删除时调用）。
     */
    int softDeleteByOrderItemId(@Param("purchaseOrderItemId") Long purchaseOrderItemId,
                                @Param("operator") String operator);
}
