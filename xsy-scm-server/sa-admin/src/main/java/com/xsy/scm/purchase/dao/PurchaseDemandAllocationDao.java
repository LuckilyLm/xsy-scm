package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 每条分配按采购明细和需求唯一确定。查询必须返回同一需求在全部采购单的活动分配，不能只计算当前采购单。
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
     * 指定需求的全部活动分配（<b>跨采购单</b>，用于重算 allocated_quantity）。
     */
    List<PurchaseDemandAllocationEntity> listActiveByDemandIds(@Param("ids") List<Long> ids);

    /**
     * 按 (itemId, demandId) 集合形态读取，用于差量对账前的旧集合装载。
     */
    List<PurchaseDemandAllocationEntity> listActiveByOrderIdAndDemandIds(@Param("purchaseOrderId") Long purchaseOrderId,
        @Param("demandIds") List<Long> demandIds);

    /**
     * 需求分配仅修改已有关系的数量，保留原关系身份和版本。SQL 必须限制原记录及版本，冲突时拒绝覆盖。
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
