package net.lab1024.sa.admin.module.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 收货与入库三张报表行（收货明细 / 入库明细 / 待入库）。
 *
 * <p>这是 R0 与参考系统最需要「借结构、不抄语义」的地方：XSY 的收货确认是商业事实，
 * 库存入账是另一条生命周期（{@code receipt_mode} + {@code putaway_status}），
 * 因此这里同时暴露 {@link ReceiptRow#receiptMode} 与 {@link ReceiptRow#putawayStatus}，
 * 页面不得把「已确认收货」显示成「已入库」。
 */
@Data
public class ReceiptReportVO {

    /** 粒度 = {@code purchase_receipt_item}。 */
    @Data
    public static class ReceiptRow {
        private Long receiptId;
        private Long receiptItemId;
        private String receiptNo;
        private String purchaseOrderNo;
        private Long purchaseOrderId;
        private String supplierName;
        private String warehouseName;
        /** {@code DIRECT} / {@code WAREHOUSE_CONFIRM}。 */
        private String receiptMode;
        /** {@code PENDING} / {@code COMPLETED}，即库存入账状态。 */
        private String putawayStatus;
        private OffsetDateTime confirmedAt;
        private String spuCode;
        private String productName;
        private String skuCode;
        private String skuName;
        private String purchaseUnit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal receivedQuantity;
        /** 该采购行在全部收货单上的累计已收量，不应用于本行求和。 */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal cumulativeReceivedQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal remainingQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal overReceiptQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal receiptDifference;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal purchasePrice;
        /** 本次收货量 × 采购单价，命名固定为「收货参考金额」，不得叫应付金额。 */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal receiptReferenceAmount;
    }

    /** 粒度 = 一条 {@code PURCHASE_IN} 流水。 */
    @Data
    public static class InboundRow {
        private Long movementId;
        private OffsetDateTime occurredAt;
        private String warehouseName;
        private String receiptNo;
        private String purchaseOrderNo;
        private String supplierName;
        private String productName;
        private String skuCode;
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal unitCost;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal costAmount;
        /** {@code unit_cost} 缺失或调用者无成本权限时为 true，页面据此显示 {@code —} 而不是 0。 */
        private Boolean costMissing;
        private String operator;
    }

    /** 粒度 = 一张待入库收货单。 */
    @Data
    public static class PendingPutawayRow {
        private Long receiptId;
        private String receiptNo;
        private String purchaseOrderNo;
        private Long purchaseOrderId;
        private String supplierName;
        private String warehouseName;
        private OffsetDateTime confirmedAt;
        private Long skuKindCount;
        /**
         * 按采购单位分组后的数量文本（如 {@code 12kg / 3箱}）。
         *
         * <p>刻意不是单个数字：一张收货单可以包含多种单位，相加得到的是无量纲合计。
         */
        private String quantityText;
    }
}
