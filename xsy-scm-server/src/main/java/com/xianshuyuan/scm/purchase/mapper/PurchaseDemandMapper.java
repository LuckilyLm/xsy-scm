package com.xianshuyuan.scm.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PurchaseDemandMapper extends BaseMapper<PurchaseDemandEntity> {
    PurchaseDemandEntity selectActiveBySalesOrderItemId(@Param("itemId") long itemId);

    PurchaseDemandEntity selectActiveBySalesOrderItemIdForUpdate(@Param("itemId") long itemId);

    PurchaseDemandEntity selectActiveByIdForUpdate(@Param("id") long id);

    Long insertIfAbsent(PurchaseDemandEntity demand);

    List<PurchaseDemandEntity> selectActivePage();
}
