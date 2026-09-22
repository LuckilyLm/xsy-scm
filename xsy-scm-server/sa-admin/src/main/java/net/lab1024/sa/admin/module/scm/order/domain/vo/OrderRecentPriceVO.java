package net.lab1024.sa.admin.module.scm.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 某客户某 SKU 的「最近已确认订单价」参考行（Wave 3 §7.5，只读辅助录单）。
 *
 * <p>取历史 CONFIRMED 订单行的 {@code locked_unit_price} 与单位快照，仅用于录单旁证：绝不回算当前价格、
 * 不参与 {@code PriceResolver} 定价、不改变价格优先级，因此不是第二套价格事实。limit 约束的是订单数而非行数，
 * 同一订单同一 SKU 的多行会各自成行返回（带 {@code itemId}），不能任取一行、相加或求平均。
 */
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
