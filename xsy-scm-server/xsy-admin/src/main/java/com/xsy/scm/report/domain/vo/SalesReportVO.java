package com.xsy.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

/**
 * 销售分析只统计已确认订单，按确认时间、结算金额计算。全局排名在分页前生成，翻到下一页仍保持原排名。
 */
@Data
public class SalesReportVO {

    /**
     * 通用 TOP N 行（金额降序，由 DB 直接 {@code LIMIT}，不取全量回 Java 排序）。
     */
    @Data
    public static class TopItem {
        private Long id;
        private String name;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal amount;
    }

    /**
     * 粒度 = SKU × 销售单位。
     */
    @Data
    public static class ProductRow {
        private Long skuId;
        private String spuCode;
        private String productName;
        private String skuCode;
        /**
         * 订单行上记录的规格，不实时回查商品主档。
         */
        private String specName;
        private String saleUnit;
        private String rootCategoryName;
        private String leafCategoryName;
        private Long orderCount;
        private Long customerCount;
        /**
         * {@code SUM(actual_quantity)}：确认数量；未回写实量的行为 NULL，不计入。
         */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal confirmedQuantity;
        /**
         * 成交均价 = 结算金额 / 确认数量，同单位内计算。
         *
         * <p>
         * 数量为 0 或 NULL 时是 {@code null} 而不是 0：0 会被读成「这个 SKU 免费卖出过」。
         */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal avgTransactionPrice;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal settlementAmount;
        /**
         * 查询范围内的该行销售金额占比（%），查询总金额为零时未知。
         */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal salesAmountSharePercent;
        /**
         * 按销售额排序的累计占比（%），在分页之前计算。
         */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal cumulativeSalesAmountSharePercent;
        private Integer amountRank;
    }

    /**
     * 粒度 = 末级分类。三级分类无 path 列，靠 {@code parent_id} 上卷两级。
     */
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
     * 粒度 = 客户。退款按 {@code xsy_order_refund.customer_id} 独立聚合后再合并，不经订单行 JOIN，否则一单多行会把退款放大。
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

    /**
     * 粒度 = 销售员；{@code seller_id} 为空归入「未分配销售员」。
     */
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

    /**
     * 粒度 = {@code xsy_sales_order}（订单表头级）。
     *
     * <p>
     * 与 {@link ItemRow} 的分工：行级回答「这单买了什么」，本行回答「这个客户有几单、每单多少」。 因此筛选语义也不同：{@code categoryId} / {@code keyword}
     * 是行级条件，在本维度下用 {@code EXISTS} 判定「该订单是否命中」；命中后金额仍按整单汇总，不会因为只匹配部分行而把订单金额算小。
     *
     * <p>
     * 退款按 {@code xsy_order_refund} 独立聚合后按订单关联，不经订单行 JOIN，否则一单多行会把退款放大。 与按客户 / 按销售员一致：退款只统计窗口内 {@code COMPLETED}
     * 的退款，不推算「净额」。
     */
    @Data
    public static class OrderRow {
        private Long orderId;
        private String orderNo;
        private OffsetDateTime confirmedAt;
        private String customerCode;
        private String customerName;
        private String sellerName;
        private String orderSource;
        private String settleMode;
        /**
         * 订单行数（{@code xsy_sales_order_item} 未删除行）。
         */
        private Long lineCount;
        /**
         * 订单涉及的 SKU 种类数。
         */
        private Long skuKindCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal settlementAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal completedRefundAmount;
    }

    /**
     * 粒度 = {@code xsy_sales_order_item}。全部业务字段取订单行当时的记录。
     */
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
        /**
         * 确认时锁定的成交单价。
         */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal lockedUnitPrice;
        private String lockedPriceSource;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal settlementLineAmount;
        private Boolean manualPriceOverride;
        private String manualPriceReason;
    }
}
