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
        private Long lotId;
        private String lotNo;
        private String sourceLotNo;
        private String packageType;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        private String unit;
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
        private Long skuId;
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

    /** 分拣任务行（绑定校验用）：商品规格、客户与实拣量都取自分拣事实。 */
    @Data
    public static class SortingItem {
        private Long taskId;
        private Long itemId;
        private Long skuId;
        private Long salesOrderId;
        private Long salesOrderItemId;
        private Long customerId;
        private String customerName;
        private String orderNo;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal sortedQuantity;
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
