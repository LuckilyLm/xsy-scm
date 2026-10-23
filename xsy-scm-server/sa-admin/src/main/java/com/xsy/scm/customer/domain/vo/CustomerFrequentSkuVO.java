package com.xsy.scm.customer.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 常购商品从近 N 天已确认订单汇总，不另存数据。不同销售单位分开统计，订购量不等于实重；最近价格只作参考，不参与当前定价。
 */
@Data
public class CustomerFrequentSkuVO {

    private Long skuId;

    /** 商品名称取该分组最近一次已确认订单行的记录，而不是任意历史行。 */
    private String skuCode;

    private String productName;

    private String specName;

    /** 销售单位是分组键之一；行内单位与总计无关，跨单位不做求和。 */
    private String unit;

    /** 订单次数 = 该 (SKU, 单位) 分组命中的已确认订单数（{@code COUNT(DISTINCT order_id)}）。 */
    private Long orderCount;

    /** 订购量：分组内 {@code ordered_quantity} 之和，四位定点字符串，不是实重或结算量。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedQuantity;

    /** 最近一次确认时间（Asia/Shanghai 日界窗口内）。 */
    private OffsetDateTime lastConfirmedAt;

    /** 最近已确认订单行的锁定单价；可能为 {@code null}，表示历史锁定价缺失，前端不兜底。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal recentUnitPrice;
}
