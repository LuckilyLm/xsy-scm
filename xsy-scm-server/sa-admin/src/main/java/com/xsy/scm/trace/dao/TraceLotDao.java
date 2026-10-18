package com.xsy.scm.trace.dao;

import com.xsy.scm.trace.domain.vo.TraceLotVO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 批次档案与收货行批次事实的读写。批次档案一经创建不改写，批次流水另表且只追加。 */
@Mapper
public interface TraceLotDao {

    @Insert("""
            INSERT INTO xsy_trace_lot
              (lot_no, source_lot_no, supplier_id, sku_id, origin_type, first_receipt_item_id, created_by)
            VALUES
              (#{lotNo}, #{sourceLotNo}, #{supplierId}, #{skuId}, #{originType}, #{firstReceiptItemId}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int createLot(TraceLotVO.Lot lot);

    @Insert("""
            INSERT INTO xsy_purchase_receipt_item_lot
              (purchase_receipt_item_id, lot_id, quantity, created_by)
            VALUES
              (#{receiptItemId}, #{lotId}, #{quantity}, #{createdBy})
            """)
    int linkReceiptItemLot(@Param("receiptItemId") Long receiptItemId, @Param("lotId") Long lotId,
            @Param("quantity") BigDecimal quantity, @Param("createdBy") String createdBy);

    @Select("""
            <script>
            SELECT l.purchase_receipt_item_id AS receiptItemId, l.lot_id AS lotId,
                   t.lot_no AS lotNo, t.source_lot_no AS sourceLotNo, l.quantity AS quantity
            FROM xsy_purchase_receipt_item_lot l
            JOIN xsy_trace_lot t ON t.id = l.lot_id
            WHERE l.purchase_receipt_item_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY l.purchase_receipt_item_id, l.id
            </script>
            """)
    List<TraceLotVO.ReceiptItemLot> listByReceiptItemIds(@Param("ids") List<Long> ids);

    @Select("""
            SELECT id, lot_no AS lotNo, source_lot_no AS sourceLotNo, supplier_id AS supplierId, sku_id AS skuId,
                   origin_type AS originType, first_receipt_item_id AS firstReceiptItemId, created_by AS createdBy
            FROM xsy_trace_lot WHERE id = #{lotId}
            """)
    TraceLotVO.Lot findById(@Param("lotId") Long lotId);
}
