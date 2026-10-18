package com.xsy.scm.inventory.dao;

import com.xsy.scm.inventory.domain.entity.InventoryLotMovementEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 批次流水只追加。
 *
 * <p>
 * 数据库侧有语句级触发器兜底：任何 UPDATE / DELETE / TRUNCATE 都会直接报错，因此这里只提供插入。
 */
@Mapper
public interface InventoryLotMovementDao {

    @Insert("""
            INSERT INTO xsy_inventory_lot_movement
              (warehouse_id, sku_id, lot_id, movement_type, source_document_type, source_document_id,
               source_document_item_id, quantity, unit_snapshot, before_quantity, after_quantity,
               occurred_at, operator, created_by)
            VALUES
              (#{warehouseId}, #{skuId}, #{lotId}, #{movementType}, #{sourceDocumentType}, #{sourceDocumentId},
               #{sourceDocumentItemId}, #{quantity}, #{unitSnapshot}, #{beforeQuantity}, #{afterQuantity},
               #{occurredAt}, #{operator}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InventoryLotMovementEntity movement);

    /**
     * 按来源单据行汇总每个批次的净额（入库为正、出库为负）。
     *
     * <p>
     * 退货按「净额」判断还能退回多少，多次部分退货因此不会把同一批次重复填满。
     */
    @Select("""
            SELECT lot_id AS lotId, SUM(quantity) AS netQuantity
            FROM xsy_inventory_lot_movement
            WHERE warehouse_id = #{warehouseId} AND sku_id = #{skuId}
              AND source_document_item_id = #{sourceDocumentItemId}
            GROUP BY lot_id
            ORDER BY lot_id
            """)
    List<LotSourceNet> sumByLotForSource(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("sourceDocumentItemId") Long sourceDocumentItemId);

    /** 来源行在某批次上的净额。 */
    record LotSourceNet(Long lotId, BigDecimal netQuantity) {
    }
}
