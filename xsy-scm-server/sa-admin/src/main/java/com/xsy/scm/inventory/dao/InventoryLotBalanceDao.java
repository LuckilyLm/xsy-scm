package com.xsy.scm.inventory.dao;

import com.xsy.scm.inventory.domain.entity.InventoryLotBalanceEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 批次余额子账的读写。SQL 见 mapper/scm/inventory/InventoryLotBalanceDao.xml。
 *
 * <p>
 * 并发口径与总账一致：先锁总账余额行，再按 {@code lot_id} 升序锁批次余额行，避免两个事务交叉等待。 批次余额只能增减，不能直接覆盖数量。
 */
@Mapper
public interface InventoryLotBalanceDao {

    /** 首建批次余额行；并发下由唯一索引兜底，冲突后仍会锁到同一行。 */
    int insertOnConflictDoNothing(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("lotId") Long lotId, @Param("unit") String unit, @Param("operator") String operator);

    InventoryLotBalanceEntity lockByWarehouseSkuLot(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("lotId") Long lotId);

    /** 出库分摊用：锁定该仓库 + 商品规格下所有正余额批次，按批次先后（先进先出）。 */
    List<InventoryLotBalanceEntity> lockPositiveByWarehouseSku(@Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId);

    int addQuantity(@Param("id") Long id, @Param("delta") BigDecimal delta, @Param("operator") String operator);
}
