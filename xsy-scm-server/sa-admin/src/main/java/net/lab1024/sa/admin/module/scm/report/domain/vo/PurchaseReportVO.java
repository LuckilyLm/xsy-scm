package net.lab1024.sa.admin.module.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 采购分析返回行（采购概览 / 按商品 / 按供应商 / 按采购员 / 采购明细 / 价格波动）。
 *
 * <p>本域刻意<b>不存在</b>「应付金额」。采购单金额是采购承诺，收货参考金额是履约事实，
 * 两者都不等于应付——应付需要收货/入账时点与核销规则，R0 没有这些事实，不能提前命名。
 */
@Data
public class PurchaseReportVO {

    /** 采购概览指标卡。 */
    @Data
    public static class Overview {
        private Long submittedOrderCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal submittedAmount;
        private Long confirmedReceiptCount;
        /** 收货数量 × 采购单价，只用于对价格与数量的交叉核对。 */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal receiptReferenceAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal purchaseInCostAmount;
        private Integer purchaseInCostMissingCount;
        /** {@code WAREHOUSE_CONFIRM + CONFIRMED + PENDING} 的收货单数。 */
        private Long pendingPutawayReceiptCount;
    }

    /**
     * 粒度 = SKU × 采购单位快照。
     *
     * <p>采购入库数量以 {@link #inboundQuantityText} 表达（按库存记账单位分组），
     * 因为它与 {@link #purchaseUnit} 可能不同单位；把两者相加会得到无量纲的合计数。
     */
    @Data
    public static class ProductRow {
        private Long skuId;
        private String spuCode;
        private String productName;
        private String skuCode;
        private String skuName;
        private String purchaseUnit;
        private Long orderCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal plannedQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal receivedQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal orderAmount;
        /** 采购成交均价 = 采购行金额合计 / 计划数量合计，不是若干单价的算术平均。 */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal avgPurchasePrice;
        private String inboundQuantityText;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal inboundCostAmount;
        private Integer inboundCostMissingCount;
    }

    /** 粒度 = 供应商。点击下钻用 {@link ProductRow}（带 supplierId 筛选）。 */
    @Data
    public static class SupplierRow {
        private Long supplierId;
        private String supplierCode;
        private String supplierName;
        private Long orderCount;
        private Long skuKindCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal orderAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal receiptReferenceAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal inboundCostAmount;
        private Integer inboundCostMissingCount;
        private OffsetDateTime lastSubmittedAt;
        private Integer amountRank;
    }

    /** 粒度 = 采购员。 */
    @Data
    public static class PurchaserRow {
        private Long purchaserId;
        private String purchaserName;
        private Long orderCount;
        private Long skuKindCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal orderAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal receiptReferenceAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal inboundCostAmount;
        private Integer inboundCostMissingCount;
        private OffsetDateTime lastSubmittedAt;
    }

    /** 粒度 = {@code purchase_order_item}。 */
    @Data
    public static class ItemRow {
        private Long purchaseOrderId;
        private Long purchaseOrderItemId;
        private String orderNo;
        private OffsetDateTime submittedAt;
        private String status;
        private String supplierName;
        private String purchaserName;
        private String warehouseName;
        /** {@code purchase_order.planned_arrival_date} 是 DATE，不是瞬间。 */
        private java.time.LocalDate plannedArrivalDate;
        private String spuCode;
        private String productName;
        private String skuCode;
        private String skuName;
        private String purchaseUnit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal plannedQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal receivedQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal purchasePrice;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal lineAmount;
    }

    /**
     * 价格波动点：粒度 = 业务日 × SKU × 采购单位。
     *
     * <p>同一天多笔按数量加权平均；不同单位永不合并成一条线（箱价与公斤价混画没有意义）。
     */
    @Data
    public static class PriceTrendPoint {
        private String bizDate;
        private Long skuId;
        private String skuCode;
        private String productName;
        private String purchaseUnit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal weightedAvgPrice;
        private Long sampleLineCount;
    }
}
