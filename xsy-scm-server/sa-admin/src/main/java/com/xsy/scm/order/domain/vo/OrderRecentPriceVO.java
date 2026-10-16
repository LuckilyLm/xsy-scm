package com.xsy.scm.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 最近成交价只作为录单参考，不参与定价或价格优先级。查询按历史订单返回，不合并同一订单内重复商品行。 */
@Data
public class OrderRecentPriceVO {

    /**
     * 订单行 ID：同单同 SKU 多行时用于区分，不能按订单号去重。
     */
    private Long itemId;

    private Long orderId;

    private String orderNo;

    /**
     * 下单时间（订单创建时间）。
     */
    private OffsetDateTime createdAt;

    /**
     * 确认时间：参考行的稳定排序键（{@code confirmed_at DESC, order_id DESC}）。
     */
    private OffsetDateTime confirmedAt;

    /**
     * 订单来源（ADMIN / MALL / MOBILE_ASSISTANT / IMPORT），沿用 {@code ScmOrderSourceEnum}。
     */
    private String orderSource;

    /**
     * 历史下单数量（订购量），非实重或结算量。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedQuantity;

    /**
     * 成交单价 = 历史订单行的 {@code locked_unit_price}（确认时锁定的结算单价快照）。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;

    /**
     * 价格来源（AGREEMENT / CUSTOMER_TYPE / MARKET / OVERRIDE），沿用 {@code ScmOrderPriceSourceEnum}；可能为空。
     */
    private String priceSource;

    /**
     * 单位快照：历史订单行的销售单位。与当前 SKU 销售单位不同时，前端只做「不可直接比较」提示，不自动换算。
     */
    private String saleUnit;
}
