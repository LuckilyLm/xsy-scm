package com.xsy.scm.sorting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 分拣任务明细行：计划量是冻结快照，分拣量与结果要么都还没有、要么都在。
 *
 * <p>
 * 这是一个**只读合并模型**，装两类来源：{@link #ORDER_ITEM}（订单行，落在 {@code sorting_task_item}）
 * 与 {@link #PROMOTION_GIFT}（满赠赠品权益，落在 {@code order_promotion_gift}）。
 * 赠品**不复制成订单行**：那会污染销售数量、商品销售排行、采购销售分析与客户购买历史。
 * 赠品行没有订单行 id、也不写回分拣量（分拣只拣货，不改赠品事实）。
 */
@Data
public class SortingTaskItemVO {

    /** 来源 = 订单行（{@code sorting_task_item}）。 */
    public static final String ORDER_ITEM = "ORDER_ITEM";

    /** 来源 = 满赠赠品权益（{@code order_promotion_gift}）。 */
    public static final String PROMOTION_GIFT = "PROMOTION_GIFT";

    private Long id;
    private Long taskId;
    private Long salesOrderId;
    /** 赠品行恒为 {@code null}：赠品不挂订单行。 */
    private Long salesOrderItemId;
    private String orderNoSnapshot;
    private Long customerId;
    private String customerNameSnapshot;
    private Long spuId;
    private Long skuId;
    private String spuCodeSnapshot;
    private String productNameSnapshot;
    private String skuCodeSnapshot;
    private String specNameSnapshot;
    private String saleUnitSnapshot;
    private String productTypeSnapshot;
    /** 赠品行装的是**赠品数量**（冻结权益），不是订单计划量。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantitySnapshot;
    /** 赠品行恒为 {@code null}：分拣不写回赠品数量，赠品事实只在冻结权益与出库流水里。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal sortedQuantity;
    private String result;
    private String reason;
    private String sortedBy;
    private OffsetDateTime sortedAt;
    private String occupationStatus;
    private Integer version;
    /** {@link #ORDER_ITEM} 或 {@link #PROMOTION_GIFT}；界面据此打「赠品」标记。 */
    private String sourceType;
}
