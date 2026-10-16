package com.xsy.scm.sorting.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 分拣清单合并订单行和促销赠品。赠品不生成销售订单行，也不回写分拣数量；计划量为建单时保存的数据。 */
@Data
public class SortingTaskItemVO {

    /** 来源 = 订单行（{@code xsy_sorting_task_item}）。 */
    public static final String ORDER_ITEM = "ORDER_ITEM";

    /** 来源 = 满赠赠品权益（{@code xsy_order_promotion_gift}）。 */
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
    /** 赠品行装的是<b>赠品数量</b>（冻结权益），不是订单计划量。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal plannedQuantitySnapshot;
    /** 赠品行恒为 {@code null}：分拣不写回赠品数量，赠品事实只在冻结权益与出库流水里。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal sortedQuantity;
    private String result;
    private String reason;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String sortedBy;
    private OffsetDateTime sortedAt;
    private String occupationStatus;
    private Integer version;
    /** {@link #ORDER_ITEM} 或 {@link #PROMOTION_GIFT}；界面据此打「赠品」标记。 */
    private String sourceType;
}
