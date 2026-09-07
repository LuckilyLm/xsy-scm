package com.xianshuyuan.scm.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.inventory.entity.InventoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InventoryMapper extends BaseMapper<InventoryEntity> {
 int insertIfAbsent(@Param("warehouseId") long warehouseId,@Param("skuId") long skuId,@Param("warehouseCode") String warehouseCode,@Param("warehouseName") String warehouseName,@Param("skuCode") String skuCode,@Param("skuName") String skuName,@Param("unit") String unit);
 InventoryEntity selectByWarehouseAndSkuForUpdate(@Param("warehouseId") long warehouseId,@Param("skuId") long skuId);
 int increase(@Param("id") long id,@Param("version") int version,@Param("quantity") java.math.BigDecimal quantity);
 List<InventoryEntity> selectActiveList(@Param("warehouseId") Long warehouseId,@Param("skuId") Long skuId);
}
