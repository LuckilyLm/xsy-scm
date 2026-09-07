package com.xianshuyuan.scm.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.inventory.entity.InventoryMovementEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryMovementMapper extends BaseMapper<InventoryMovementEntity> {
    String nextNumber();

    InventoryMovementEntity selectBySource(@Param("receiptId") long receiptId, @Param("receiptItemId") long receiptItemId, @Param("confirmationId") long confirmationId);

    List<InventoryMovementEntity> selectActiveList(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId, @Param("sourceId") Long sourceId);
}
