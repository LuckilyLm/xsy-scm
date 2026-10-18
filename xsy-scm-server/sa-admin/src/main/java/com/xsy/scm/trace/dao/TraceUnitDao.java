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
