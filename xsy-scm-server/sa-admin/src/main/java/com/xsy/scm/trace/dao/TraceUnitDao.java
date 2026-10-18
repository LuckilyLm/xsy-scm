package com.xsy.scm.trace.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.trace.domain.form.TraceUnitQueryForm;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 包装码与包装事件的读写。包装事件只追加；包装码可作废但不可删除、码不复用。 */
@Mapper
public interface TraceUnitDao {

    @Insert("""
            INSERT INTO xsy_trace_unit
              (unit_code, lot_id, package_type, quantity, unit, status, remark, created_by)
            VALUES
              (#{unitCode}, #{lotId}, #{packageType}, #{quantity}, #{unit}, 'ACTIVE', #{remark}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int createUnit(TraceUnitVO.Unit unit);

    @Insert("""
            INSERT INTO xsy_trace_event
              (unit_id, lot_id, event_type, source_document_type, source_document_id,
               occurred_at, operator, remark, created_by)
            VALUES
              (#{unitId}, #{lotId}, 'ISSUE', NULL, NULL, #{occurredAt}, #{operator}, #{remark}, #{operator})
            """)
    int appendIssueEvent(@Param("unitId") Long unitId, @Param("lotId") Long lotId,
            @Param("occurredAt") OffsetDateTime occurredAt, @Param("operator") String operator,
            @Param("remark") String remark);

    @Select("""
            SELECT t.id AS lotId, t.lot_no AS lotNo, t.source_lot_no AS sourceLotNo, t.sku_id AS skuId,
                   k.sku_code AS skuCode, k.spec_name AS skuName
            FROM xsy_trace_lot t
            LEFT JOIN xsy_product_sku k ON k.id = t.sku_id
            WHERE t.id = #{lotId}
            """)
    TraceUnitVO.Lot findLot(@Param("lotId") Long lotId);

    /** 该批次已发码的包装数量之和（含已作废的码：作废不等于没发过）。 */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM xsy_trace_unit WHERE lot_id = #{lotId}")
    BigDecimal sumIssuedByLot(@Param("lotId") Long lotId);

    /** 批次余额行上的记账单位；发码沿用同一个单位，不做换算。 */
    @Select("SELECT unit FROM xsy_inventory_lot_balance WHERE lot_id = #{lotId} LIMIT 1")
    String lotUnit(@Param("lotId") Long lotId);

    /** 该批次当前余额（跨仓库求和）：发码总量不得超过它。 */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM xsy_inventory_lot_balance WHERE lot_id = #{lotId}")
    BigDecimal lotBalance(@Param("lotId") Long lotId);

    @Select("""
            <script>
            SELECT u.id, u.unit_code AS unitCode, u.lot_id AS lotId, t.lot_no AS lotNo,
                   t.source_lot_no AS sourceLotNo, u.package_type AS packageType, u.quantity, u.unit,
                   u.status, u.remark, u.created_at AS createdAt, u.created_by AS createdBy
            FROM xsy_trace_unit u
            LEFT JOIN xsy_trace_lot t ON t.id = u.lot_id
            WHERE 1 = 1
            <if test="query.lotId != null">AND u.lot_id = #{query.lotId}</if>
            <if test="query.unitCode != null and query.unitCode != ''">
                AND u.unit_code ILIKE CONCAT('%', #{query.unitCode}, '%')
            </if>
            <if test="query.lotNo != null and query.lotNo != ''">
                AND t.lot_no ILIKE CONCAT('%', #{query.lotNo}, '%')
            </if>
            <if test="query.status != null and query.status != ''">AND u.status = #{query.status}</if>
            ORDER BY u.id DESC
            </script>
            """)
    List<TraceUnitVO.Unit> queryPage(Page<?> page, @Param("query") TraceUnitQueryForm query);

    @Select("""
            SELECT u.id, u.unit_code AS unitCode, u.lot_id AS lotId, t.lot_no AS lotNo,
                   t.source_lot_no AS sourceLotNo, u.package_type AS packageType, u.quantity, u.unit,
                   u.status, u.remark, u.created_at AS createdAt, u.created_by AS createdBy
            FROM xsy_trace_unit u
            LEFT JOIN xsy_trace_lot t ON t.id = u.lot_id
            WHERE u.unit_code = #{unitCode}
            """)
    TraceUnitVO.Unit findByCode(@Param("unitCode") String unitCode);

    /** 批次首次入库的收货来源。 */
    @Select("""
            SELECT r.receipt_no AS receiptNo, r.supplier_name_snapshot AS supplierName, r.confirmed_at AS confirmedAt
            FROM xsy_purchase_receipt_item_lot l
            JOIN xsy_purchase_receipt_item i ON i.id = l.purchase_receipt_item_id
            JOIN xsy_purchase_receipt r ON r.id = i.purchase_receipt_id
            WHERE l.lot_id = #{lotId} AND r.deleted = FALSE
            ORDER BY r.confirmed_at DESC NULLS LAST, r.id DESC
            LIMIT 1
            """)
    TraceUnitVO.Receipt receiptOfLot(@Param("lotId") Long lotId);

    @Select("""
            SELECT u.id, u.unit_code AS unitCode, u.lot_id AS lotId, t.lot_no AS lotNo,
                   t.source_lot_no AS sourceLotNo, u.package_type AS packageType, u.quantity, u.unit,
                   u.status, u.remark, u.created_at AS createdAt, u.created_by AS createdBy
            FROM xsy_trace_unit u
            LEFT JOIN xsy_trace_lot t ON t.id = u.lot_id
            WHERE u.id = #{id}
            """)
    TraceUnitVO.Unit findById(@Param("id") Long id);

    /** 分拣任务行：绑定校验的客户/商品/实拣量来源。 */
    @Select("""
            SELECT i.task_id AS taskId, i.id AS itemId, i.sku_id AS skuId, i.sales_order_id AS salesOrderId,
                   i.sales_order_item_id AS salesOrderItemId, i.customer_id AS customerId,
                   i.customer_name_snapshot AS customerName, i.order_no_snapshot AS orderNo,
                   i.sorted_quantity AS sortedQuantity
            FROM xsy_sorting_task_item i
            WHERE i.id = #{itemId} AND i.deleted = FALSE
            """)
    TraceUnitVO.SortingItem findSortingItem(@Param("itemId") Long itemId);

    /** 该分拣行已绑定的包装数量之和：绑定总量不得超过实拣量。 */
    @Select("""
            SELECT COALESCE(SUM(u.quantity), 0)
            FROM xsy_trace_unit_binding b
            JOIN xsy_trace_unit u ON u.id = b.unit_id
            WHERE b.sorting_task_item_id = #{itemId}
            """)
    BigDecimal sumBoundBySortingItem(@Param("itemId") Long itemId);

    @Insert("""
            INSERT INTO xsy_trace_unit_binding
              (unit_id, lot_id, sorting_task_id, sorting_task_item_id, sales_order_id, sales_order_item_id,
               customer_id, customer_name_snapshot, order_no_snapshot, bound_at, bound_by, created_by)
            VALUES
              (#{unitId}, #{lotId}, #{taskId}, #{itemId}, #{salesOrderId}, #{salesOrderItemId},
               #{customerId}, #{customerName}, #{orderNo}, #{boundAt}, #{operator}, #{operator})
            """)
    int insertBinding(@Param("unitId") Long unitId, @Param("lotId") Long lotId, @Param("taskId") Long taskId,
            @Param("itemId") Long itemId, @Param("salesOrderId") Long salesOrderId,
            @Param("salesOrderItemId") Long salesOrderItemId, @Param("customerId") Long customerId,
            @Param("customerName") String customerName, @Param("orderNo") String orderNo,
            @Param("boundAt") OffsetDateTime boundAt, @Param("operator") String operator);

    @Insert("""
            INSERT INTO xsy_trace_event
              (unit_id, lot_id, event_type, source_document_type, source_document_id,
               occurred_at, operator, created_by)
            VALUES
              (#{unitId}, #{lotId}, 'BIND', 'SORTING_TASK_ITEM', #{itemId}, #{occurredAt}, #{operator}, #{operator})
            """)
    int appendBindEvent(@Param("unitId") Long unitId, @Param("lotId") Long lotId, @Param("itemId") Long itemId,
            @Param("occurredAt") OffsetDateTime occurredAt, @Param("operator") String operator);

    @Update("""
            UPDATE xsy_trace_unit
            SET status = 'VOID'
            WHERE id = #{id} AND status = 'ACTIVE'
            """)
    int markVoid(@Param("id") Long id);

    @Insert("""
            INSERT INTO xsy_trace_event
              (unit_id, lot_id, event_type, occurred_at, operator, remark, created_by)
            VALUES
              (#{unitId}, #{lotId}, 'VOID', #{occurredAt}, #{operator}, #{reason}, #{operator})
            """)
    int appendVoidEvent(@Param("unitId") Long unitId, @Param("lotId") Long lotId,
            @Param("occurredAt") OffsetDateTime occurredAt, @Param("operator") String operator,
            @Param("reason") String reason);

    /** 该包装码的绑定事实（未绑定返回 null）。 */
    @Select("""
            SELECT b.sorting_task_id AS sortingTaskId, b.sorting_task_item_id AS sortingTaskItemId,
                   b.order_no_snapshot AS orderNo, b.customer_name_snapshot AS customerName,
                   b.bound_at AS boundAt, b.bound_by AS boundBy
            FROM xsy_trace_unit_binding b
            WHERE b.unit_id = #{unitId}
            """)
    TraceUnitVO.Binding findBinding(@Param("unitId") Long unitId);

    /** 该批次参与过的销售出库（按批次流水汇总到出库单）。 */
    @Select("""
            SELECT o.outbound_no AS outboundNo, m.occurred_at AS occurredAt, SUM(-m.quantity) AS quantity
            FROM xsy_inventory_lot_movement m
            JOIN xsy_inventory_outbound o ON o.id = m.source_document_id
            WHERE m.lot_id = #{lotId} AND m.movement_type = 'SALES_OUT'
            GROUP BY o.outbound_no, m.occurred_at
            ORDER BY m.occurred_at DESC
            LIMIT 20
            """)
    List<TraceUnitVO.Outbound> outboundsOfLot(@Param("lotId") Long lotId);
}
