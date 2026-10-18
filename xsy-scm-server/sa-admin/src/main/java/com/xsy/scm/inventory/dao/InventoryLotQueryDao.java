package com.xsy.scm.inventory.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.inventory.domain.form.InventoryLotBalanceQueryForm;
import com.xsy.scm.inventory.domain.form.InventoryLotMovementQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryLotBalanceVO;
import com.xsy.scm.inventory.domain.vo.InventoryLotMovementVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 批次库存与批次流水的只读查询。
 *
 * <p>
 * 仓库数据范围由 Service 显式下传：{@code scope} 为 null 时必须恒假，否则任何漏传范围的调用点都会静默变成全仓可见 （与余额查询同一条失败关闭口径）。
 */
@Mapper
public interface InventoryLotQueryDao {

    /** 批次余额分页：只列出仍有数量的批次，零余额批次留给流水回看。 */
    @Select("""
            <script>
            SELECT b.id,
                   b.warehouse_id AS warehouseId,
                   w.warehouse_code AS warehouseCode,
                   w.name AS warehouseName,
                   b.sku_id AS skuId,
                   k.sku_code AS skuCode,
                   k.spec_name AS skuName,
                   p.name AS productName,
                   b.lot_id AS lotId,
                   t.lot_no AS lotNo,
                   t.source_lot_no AS sourceLotNo,
                   b.unit,
                   b.quantity,
                   b.updated_at AS updatedAt
            FROM xsy_inventory_lot_balance b
            LEFT JOIN xsy_warehouse w ON w.id = b.warehouse_id
            LEFT JOIN xsy_product_sku k ON k.id = b.sku_id
            LEFT JOIN xsy_product_spu p ON p.id = k.spu_id
            LEFT JOIN xsy_trace_lot t ON t.id = b.lot_id
            WHERE b.quantity &gt; 0
            <if test="scope == null">AND FALSE</if>
            <if test="scope != null and scope.all == false">
                AND b.warehouse_id IN
                <foreach collection="scope.ids" item="scopeId" open="(" separator="," close=")">#{scopeId}</foreach>
            </if>
            <if test="query.warehouseId != null">AND b.warehouse_id = #{query.warehouseId}</if>
            <if test="query.skuId != null">AND b.sku_id = #{query.skuId}</if>
            <if test="query.lotNo != null and query.lotNo != ''">
                AND t.lot_no ILIKE CONCAT('%', #{query.lotNo}, '%')
            </if>
            <if test="query.sourceLotNo != null and query.sourceLotNo != ''">
                AND t.source_lot_no ILIKE CONCAT('%', #{query.sourceLotNo}, '%')
            </if>
            <if test="query.productName != null and query.productName != ''">
                AND p.name ILIKE CONCAT('%', #{query.productName}, '%')
            </if>
            ORDER BY b.warehouse_id, b.sku_id, b.lot_id
            </script>
            """)
    List<InventoryLotBalanceVO> queryBalancePage(Page<?> page, @Param("query") InventoryLotBalanceQueryForm query,
            @Param("scope") ScmValueScope scope);

    /** 批次流水分页：按发生时刻倒序，保留有符号数量。 */
    @Select("""
            <script>
            SELECT m.id,
                   m.warehouse_id AS warehouseId,
                   w.name AS warehouseName,
                   m.sku_id AS skuId,
                   k.sku_code AS skuCode,
                   k.spec_name AS skuName,
                   m.lot_id AS lotId,
                   t.lot_no AS lotNo,
                   m.movement_type AS movementType,
                   m.source_document_type AS sourceDocumentType,
                   m.source_document_id AS sourceDocumentId,
                   m.source_document_item_id AS sourceDocumentItemId,
                   m.quantity,
                   m.unit_snapshot AS unitSnapshot,
                   m.before_quantity AS beforeQuantity,
                   m.after_quantity AS afterQuantity,
                   m.occurred_at AS occurredAt,
                   m.operator
            FROM xsy_inventory_lot_movement m
            LEFT JOIN xsy_warehouse w ON w.id = m.warehouse_id
            LEFT JOIN xsy_product_sku k ON k.id = m.sku_id
            LEFT JOIN xsy_trace_lot t ON t.id = m.lot_id
            WHERE 1 = 1
            <if test="scope == null">AND FALSE</if>
            <if test="scope != null and scope.all == false">
                AND m.warehouse_id IN
                <foreach collection="scope.ids" item="scopeId" open="(" separator="," close=")">#{scopeId}</foreach>
            </if>
            <if test="query.warehouseId != null">AND m.warehouse_id = #{query.warehouseId}</if>
            <if test="query.skuId != null">AND m.sku_id = #{query.skuId}</if>
            <if test="query.lotNo != null and query.lotNo != ''">
                AND t.lot_no ILIKE CONCAT('%', #{query.lotNo}, '%')
            </if>
            <if test="query.movementType != null and query.movementType != ''">
                AND m.movement_type = #{query.movementType}
            </if>
            ORDER BY m.occurred_at DESC, m.id DESC
            </script>
            """)
    List<InventoryLotMovementVO> queryMovementPage(Page<?> page, @Param("query") InventoryLotMovementQueryForm query,
            @Param("scope") ScmValueScope scope);
}
