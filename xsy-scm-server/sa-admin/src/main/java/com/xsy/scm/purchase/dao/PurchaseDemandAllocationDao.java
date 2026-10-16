package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/** 每条分配按采购明细和需求唯一确定。查询必须返回同一需求在全部采购单的活动分配，不能只计算当前采购单。 */
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
     * 指定需求的全部活动分配（<b>跨采购单</b>，用于重算 allocated_quantity）。
     */
    List<PurchaseDemandAllocationEntity> listActiveByDemandIds(@Param("ids") List<Long> ids);

    /**
     * 按 (itemId, demandId) 集合形态读取，用于差量对账前的旧集合装载。
     */
    List<PurchaseDemandAllocationEntity> listActiveByOrderIdAndDemandIds(@Param("purchaseOrderId") Long purchaseOrderId,
            @Param("demandIds") List<Long> demandIds);

    /**
     * 只改数量（<b>保留的 allocation</b>， B 段第 9 步：不重建行）。
     *
     * <p>
     * <b>刻意不用 MP 的 {@code updateById}</b>：{@code @Version} 的乐观锁要求实体上的 {@code version} 非空，而差量对象是从「请求」构造出来的（请求不带
     * allocation 版本），一旦漏设 version， MP 会<b>静默跳过</b>版本条件、把并发写变成覆盖写。这里把版本作为显式参数传给 SQL，影响行数为 0 就一定是并发冲突 —— 与
     * {@link #softDelete} 同一形状。
     */
    int updateQuantity(@Param("id") Long id, @Param("version") Integer version,
            @Param("allocatedQuantity") BigDecimal allocatedQuantity, @Param("operator") String operator);

    /**
     * 软删单条（<b>只删这一条</b>，不波及同一行的其它分配）。
     */
    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    /**
     * 软删某个采购行的全部分配（行被删除时调用）。
     */
    int softDeleteByOrderItemId(@Param("purchaseOrderItemId") Long purchaseOrderItemId,
            @Param("operator") String operator);
}
