package com.xsy.scm.purchase.dao;

import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO.Inquiry;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO.Quote;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PurchaseInquiryDao {
    @Insert("""
            INSERT INTO xsy_purchase_inquiry
              (batch_id, warehouse_id, purchaser_id, sku_id, sku_code_snapshot,
               product_name_snapshot, demand_unit, required_quantity, deadline_at, created_by)
            VALUES
              (#{batchId}, #{warehouseId}, #{purchaserId}, #{skuId}, #{skuCodeSnapshot},
               #{productNameSnapshot}, #{demandUnit}, #{requiredQuantity}, #{deadlineAt}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int create(Inquiry inquiry);

    @Select("SELECT * FROM xsy_purchase_inquiry WHERE id = #{id}")
    Inquiry find(@Param("id") Long id);

    @Select("SELECT * FROM xsy_purchase_inquiry WHERE id = #{id} FOR UPDATE")
    Inquiry lock(@Param("id") Long id);

    @Select("SELECT * FROM xsy_purchase_inquiry WHERE batch_id = #{batchId} ORDER BY id")
    List<Inquiry> byBatch(@Param("batchId") Long batchId);

    @Select("""
            SELECT * FROM xsy_purchase_inquiry WHERE batch_id = #{batchId}
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Inquiry> pageByBatch(@Param("batchId") Long batchId, @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("SELECT count(*) FROM xsy_purchase_inquiry WHERE batch_id = #{batchId}")
    long countByBatch(@Param("batchId") Long batchId);

    @Update("""
            UPDATE xsy_purchase_inquiry SET version = version + 1
            WHERE id = #{id} AND version = #{version} AND status = 'OPEN'
            """)
    int bump(@Param("id") Long id, @Param("version") Integer version);

    @Update("""
            UPDATE xsy_purchase_inquiry
            SET status = 'ACCEPTED', accepted_quote_id = #{quoteId},
                accepted_at = CURRENT_TIMESTAMP, accepted_by = #{operator}, version = version + 1
            WHERE id = #{id} AND version = #{version} AND status = 'OPEN'
            """)
    int accept(@Param("id") Long id, @Param("version") Integer version,
            @Param("quoteId") Long quoteId, @Param("operator") String operator);

    @Insert("""
            INSERT INTO xsy_purchase_inquiry_quote
              (inquiry_id, supplier_id, supplier_name_snapshot, revision, purchase_unit,
               unit_price, valid_until, remark, recorded_by)
            VALUES
              (#{inquiryId}, #{supplierId}, #{supplierNameSnapshot}, #{revision}, #{purchaseUnit},
               #{unitPrice}, #{validUntil}, #{remark}, #{recordedBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int quote(Quote quote);

    @Select("""
            SELECT COALESCE(MAX(revision), 0) FROM xsy_purchase_inquiry_quote
            WHERE inquiry_id = #{inquiryId} AND supplier_id = #{supplierId}
            """)
    int maxRevision(@Param("inquiryId") Long inquiryId, @Param("supplierId") Long supplierId);

    @Select("SELECT * FROM xsy_purchase_inquiry_quote WHERE id = #{id} AND inquiry_id = #{inquiryId}")
    Quote quoteById(@Param("id") Long id, @Param("inquiryId") Long inquiryId);

    @Select("""
            SELECT * FROM xsy_purchase_inquiry_quote
            WHERE inquiry_id = #{inquiryId} AND supplier_id = #{supplierId}
            ORDER BY revision DESC LIMIT 1
            """)
    Quote latestSupplierQuote(@Param("inquiryId") Long inquiryId, @Param("supplierId") Long supplierId);

    @Select("""
            SELECT * FROM (
              SELECT DISTINCT ON (supplier_id) * FROM xsy_purchase_inquiry_quote
              WHERE inquiry_id = #{inquiryId}
              ORDER BY supplier_id, revision DESC
            ) latest
            WHERE valid_until > CURRENT_TIMESTAMP
            ORDER BY unit_price ASC, supplier_id ASC
            """)
    List<Quote> currentOffers(@Param("inquiryId") Long inquiryId);

    @Select("""
            <script>
            SELECT * FROM xsy_purchase_inquiry_quote
            WHERE inquiry_id = #{inquiryId}
            <if test="beforeId != null">AND id &lt; #{beforeId}</if>
            ORDER BY id DESC LIMIT #{limit}
            </script>
            """)
    List<Quote> history(@Param("inquiryId") Long inquiryId, @Param("beforeId") Long beforeId,
            @Param("limit") int limit);
}
