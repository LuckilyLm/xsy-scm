package com.xianshuyuan.scm.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandAllocationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PurchaseDemandAllocationMapper extends BaseMapper<PurchaseDemandAllocationEntity> {
    List<PurchaseDemandAllocationEntity> selectActiveByDemandId(@Param("demandId") long demandId);

    PurchaseDemandAllocationEntity selectActiveByOrderItemAndDemandForUpdate(
            @Param("orderItemId") long orderItemId,
            @Param("demandId") long demandId
    );

    PurchaseDemandAllocationEntity selectActiveByOrderItemIdForUpdate(
            @Param("orderItemId") long orderItemId
    );

    List<PurchaseDemandAllocationEntity> selectActiveByOrderId(@Param("orderId") long orderId);

    List<PurchaseDemandAllocationEntity> selectActiveByOrderIdForUpdate(@Param("orderId") long orderId);

    int softDeleteWithVersion(
            @Param("id") long id,
            @Param("version") int version
    );
}
