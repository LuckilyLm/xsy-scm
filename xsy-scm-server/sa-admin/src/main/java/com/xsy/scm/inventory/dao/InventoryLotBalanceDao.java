package com.xsy.scm.inventory.dao;

import com.xsy.scm.inventory.domain.entity.InventoryLotBalanceEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 批次余额子账的读写。
 *
 * <p>
 * 并发口径与总账一致：先锁总账余额行，再按 {@code lot_id} 升序锁批次余额行，避免两个事务交叉等待。 批次余额只能增减，不能直接覆盖数量。
 */
@Mapper
public interface InventoryLotBalanceDao {

    /** 首建批次余额行；并发下由唯一索引兜底，冲突后仍会锁到同一行。 */
    @Insert("""
            INSERT INTO xsy_inventory_lot_balance
              (warehouse_id, sku_id, lot_id, unit, quantity, version, created_by, updated_by)
            VALUES
              (#{warehouseId}, #{skuId}, #{lotId}, #{unit}, 0, 0, #{operator}, #{operator})
            ON CONFLICT (warehouse_id, sku_id, lot_id) DO NOTHING
            """)
    int insertOnConflictDoNothing(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("lotId") Long lotId, @Param("unit") String unit, @Param("operator") String operator);

    @Select("""
            SELECT id, warehouse_id AS warehouseId, sku_id AS skuId, lot_id AS lotId, unit, quantity, version,
                   created_at AS createdAt, updated_at AS updatedAt, created_by AS createdBy, updated_by AS updatedBy
            FROM xsy_inventory_lot_balance
            WHERE warehouse_id = #{warehouseId} AND sku_id = #{skuId} AND lot_id = #{lotId}
            FOR UPDATE
            """)
    InventoryLotBalanceEntity lockByWarehouseSkuLot(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("lotId") Long lotId);

    /** 出库分摊用：锁定该仓库 + 商品规格下所有正余额批次，按批次先后（先进先出）。 */
    @Select("""
            SELECT id, warehouse_id AS warehouseId, sku_id AS skuId, lot_id AS lotId, unit, quantity, version,
                   created_at AS createdAt, updated_at AS updatedAt, created_by AS createdBy, updated_by AS updatedBy
            FROM xsy_inventory_lot_balance
            WHERE warehouse_id = #{warehouseId} AND sku_id = #{skuId} AND quantity > 0
            ORDER BY lot_id
            FOR UPDATE
            """)
    List<InventoryLotBalanceEntity> lockPositiveByWarehouseSku(@Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId);

    @Update("""
            UPDATE xsy_inventory_lot_balance
            SET quantity = quantity + #{delta}, version = version + 1,
                updated_at = CURRENT_TIMESTAMP, updated_by = #{operator}
            WHERE id = #{id} AND quantity + #{delta} >= 0
            """)
    int addQuantity(@Param("id") Long id, @Param("delta") BigDecimal delta, @Param("operator") String operator);
}
