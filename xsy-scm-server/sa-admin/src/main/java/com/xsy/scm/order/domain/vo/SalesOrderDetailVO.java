package com.xsy.scm.order.domain.vo;

import com.xsy.scm.promotion.domain.vo.OrderDiscountVO;
import com.xsy.scm.promotion.domain.vo.PromotionDiscountVO;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderDetailVO extends SalesOrderVO {
    private List<SalesOrderItemVO> items;
    private OrderAddressSnapshotVO address;

    /**
     * 订单优惠；没有优惠时为 {@code null}。
     *
     * <p>
     * 订单金额列仍是结算金额（不含优惠），优惠单独返回：合并成一个净额会让「原价多少、减了多少」无法回答，退款也就没有可对账的依据。
     */
    private OrderDiscountVO discount;

    /**
     * 满赠赠品；没有赠品时为空列表。
     *
     * <p>
     * 与 {@link #discount} 分开：赠品不是金额优惠，只有赠品没有优惠的订单需要单独展示。
     */
    private List<PromotionDiscountVO.GiftEntitlementVO> gifts;

    /**
     * null 表示当前调用者没有该领域的查询权限；空列表表示授权范围内没有数据。
     */
    private List<ReservationFact> inventoryReservations;
    private List<SortingFact> sortingProgress;
    private DeliveryFact deliveryProgress;
    private List<ReturnFact> returns;
    private List<RefundFact> refunds;
    private Boolean inventoryFactsVisible;
    private Boolean sortingFactsVisible;
    private Boolean deliveryFactsVisible;
    private Boolean returnFactsVisible;
    private Boolean refundFactsVisible;
    private Boolean receivableFactsVisible;
    private Boolean fundingFactsVisible;
    private FundingFact funding;

    @Data
    public static class FundingFact {
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal payableAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal receivedAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal remainingAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal excessReceivedAmount;
    }

    @Data
    public static class ReservationFact {
        private Long reservationId;
        private Long orderItemId;
        private String productName;
        private String skuName;
        private String warehouseName;
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        private String status;
        private OffsetDateTime occurredAt;
    }

    @Data
    public static class SortingFact {
        private Long taskId;
        private String taskNo;
        private String taskStatus;
        private Long orderItemId;
        private String productName;
        private String skuName;
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal plannedQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal sortedQuantity;
        private String result;
        private OffsetDateTime sortedAt;
    }

    @Data
    public static class DeliveryFact {
        private Long routeId;
        private String routeNo;
        private String routeStatus;
        private String fulfillmentStatus;
        private String warehouseName;
        private OffsetDateTime dispatchedAt;
        private OffsetDateTime signedAt;
        private String signReason;
    }

    @Data
    public static class ReturnFact {
        private Long returnId;
        private String returnNo;
        private String status;
        private String reason;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal approvedAmount;
        private OffsetDateTime approvedAt;
        /**
         * null 表示无应收查询权限，false 表示没有红字应收。
         */
        private Boolean redReceivableRecorded;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal redReceivableAmount;
    }

    @Data
    public static class RedReceivableFact {
        private Long returnId;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal amount;
    }

    @Data
    public static class RefundFact {
        private Long refundId;
        private Long returnId;
        private String returnNo;
        private String refundNo;
        private String status;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal refundAmount;
        private OffsetDateTime completedAt;
        /**
         * 资金分摊状态；没有资金记录时为 NO_FACT，退款单完成不等于已到账。
         */
        private String fundingStatus;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal fundingSucceededAmount;
    }

    @Data
    public static class RefundFundingFact {
        private Long refundId;
        private Long sourceCount;
        private Long succeededCount;
        private Long failedCount;
        private Long processingCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal succeededAmount;
    }
}
