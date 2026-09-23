package net.lab1024.sa.admin.module.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 销售分析各维度的返回行集合（按商品 / 按分类 / 按客户 / 按销售员 / 订单明细）。
 *
 * <p><b>全类共同遵守的口径</b>：只统计 {@code status=CONFIRMED} 的订单，业务日是
 * {@code confirmed_at}，金额只取 {@code settlement_*}。{@code ordered_*} 在本域完全不出现，
 * 避免出现「同一页面两个金额、不知哪个算数」的情况。
 *
 * <p>行数排名（{@code amountRank}）由 SQL 窗口函数在<b>分页之前</b>算出，所以第 2 页的排名
 * 仍是全局排名，不是页内排名。
 */
@Data
public class SalesReportVO {

    /** 通用 TOP N 行（金额降序，由 DB 直接 {@code LIMIT}，不取全量回 Java 排序）。 */
    @Data
    public static class TopItem {
        private Long id;
        private String name;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal amount;
    }

    /** 粒度 = SKU × 销售单位快照。 */
    @Data
    public static class ProductRow {
        private Long skuId;
        private String spuCode;
        private String productName;
        private String skuCode;
        /** 订单行上的规格快照，不实时回查商品主档。 */
        private String specName;
        private String saleUnit;
        private String rootCategoryName;
        private String leafCategoryName;
        private Long orderCount;
        private Long customerCount;
        /** {@code SUM(actual_quantity)}：确认数量；未回写实量的行为 NULL，不计入。 */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal confirmedQuantity;
        /**
         * 成交均价 = 结算金额 / 确认数量，同单位内计算。
         *
         * <p>数量为 0 或 NULL 时是 {@code null} 而不是 0：0 会被读成「这个 SKU 免费卖出过」。
         */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal avgTransactionPrice;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal settlementAmount;
        private Integer amountRank;
    }

    /** 粒度 = 末级分类。三级分类无 path 列，靠 {@code parent_id} 上卷两级。 */
    @Data
    public static class CategoryRow {
        private Long categoryId;
        private String rootCategoryName;
        private String leafCategoryName;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal settlementAmount;
        private Long orderCount;
        private Long customerCount;
        private Integer amountRank;
    }

    /**
     * 粒度 = 客户。退款按 {@code order_refund.customer_id} 独立聚合后再合并，
     * 不经订单行 JOIN，否则一单多行会把退款放大。
     */
    @Data
    public static class CustomerRow {
        private Long customerId;
        private String customerCode;
        private String customerName;
        private String sellerName;
        private Long orderCount;
        private Long skuKindCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal settlementAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal completedRefundAmount;
        private OffsetDateTime lastConfirmedAt;
        private Integer amountRank;
    }

    /** 粒度 = 销售员；{@code seller_id} 为空归入「未分配销售员」。 */
    @Data
    public static class SellerRow {
        private Long sellerId;
        private String sellerName;
        private Long orderCount;
        private Long customerCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal settlementAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal completedRefundAmount;
        private OffsetDateTime lastConfirmedAt;
    }

    /** 粒度 = {@code sales_order_item}。全部业务字段取订单行快照。 */
    @Data
    public static class ItemRow {
        private Long orderId;
        private Long orderItemId;
        private String orderNo;
        private OffsetDateTime confirmedAt;
        private String customerCode;
        private String customerName;
        private String sellerName;
        private String orderSource;
        private String settleMode;
        private String spuCode;
        private String productName;
        private String skuCode;
        private String specName;
        private String productType;
        private String saleUnit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal orderedQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal actualQuantity;
        /** 确认时锁定的成交单价。 */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal lockedUnitPrice;
        private String lockedPriceSource;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal settlementLineAmount;
        private Boolean manualPriceOverride;
        private String manualPriceReason;
    }
}
