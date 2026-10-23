package com.xsy.scm.trace.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.Data;

/**
 * 包装码（一物一码）的视图。
 *
 * <p>
 * 一个包装码只属于一个批次（一码一批）；{@code unitCode} 由系统发号，不透明、不嵌日期、永不复用。
 */
public final class TraceUnitVO {

    private TraceUnitVO() {
    }

    /** 包装单元行。 */
    @Data
    public static class Unit {
        private Long id;
        private String unitCode;
        /** 标签二维码里的公开凭据；与 unitCode 分开，泄露 unitCode 不等于泄露扫码入口。 */
        private String publicToken;
        private Long lotId;
        private String lotNo;
        private String sourceLotNo;
        private String packageType;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        private String unit;
        /** 净重；未称重时为 null，页面展示「待称重」。 */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal netWeight;
        private String weightUnit;
        /** SCALE 表示来自电子秤，MANUAL 表示后台补录。 */
        private String weightSource;
        private OffsetDateTime measuredAt;
        /** 来源称重筐；为空表示这枚码没有经过电子秤。 */
        private Long weighingEventId;
        private String status;
        private String remark;
        private OffsetDateTime createdAt;
        private String createdBy;
    }

    /** 批次信息（回放用）。 */
    @Data
    public static class Lot {
        private Long lotId;
        private String lotNo;
        private String sourceLotNo;
        /** 批次所属商品：商品资质按这个 id 挂载。 */
        private Long spuId;
        private Long skuId;
        private String spuName;
        private String skuCode;
        private String skuName;
    }

    /** 收货来源（回放用）：批次首次入库的那张收货单。 */
    @Data
    public static class Receipt {
        private String receiptNo;
        private String supplierName;
        private OffsetDateTime confirmedAt;
    }

    /** 出库去向（回放用）：该批次参与过的出库单。 */
    @Data
    public static class Outbound {
        private String outboundNo;
        private OffsetDateTime occurredAt;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
    }

    /** 分拣任务行（绑定校验用）：商品规格、客户与实拣量都取自分拣记录。 */
    @Data
    public static class SortingItem {
        private Long taskId;
        private Long itemId;
        private String taskStatus;
        private Long warehouseId;
        private Long skuId;
        private Long salesOrderId;
        private Long salesOrderItemId;
        private Long customerId;
        private String customerName;
        private String orderNo;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal sortedQuantity;
        /** 实拣量的销售单位（分拣行当时的记录）。 */
        private String saleUnit;
        /** 订单行确认时保存的库存记账单位与换算比例；为空表示该单没有保存换算。 */
        private String stockUnit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal stockUnitsPerSaleUnit;
    }

    /** 分拣绑定：这个码在分拣现场被绑到了哪张任务行的哪个客户。 */
    @Data
    public static class Binding {
        private Long sortingTaskId;
        private Long sortingTaskItemId;
        private String orderNo;
        private String customerName;
        private OffsetDateTime boundAt;
        private String boundBy;
    }

    /** 秤端选批次用的候选：本仓该商品仍有余额的批次。 */
    @Data
    public static class LotOption {
        private Long lotId;
        private String lotNo;
        private String sourceLotNo;
        /** 批次余额的记账单位，发码与称重换算都以它为准。 */
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal balance;
    }

    /** 一个包装码的完整回放：单元 → 批次 → 收货来源 → 分拣绑定 → 出库去向。 */
    @Data
    public static class Replay {
        private Unit unit;
        private Lot lot;
        private Receipt receipt;
        private Binding binding;
        private List<Outbound> outbounds;
    }
}
