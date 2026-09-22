package net.lab1024.sa.admin.module.scm.customer.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 客户「常购商品」聚合行（Wave 7 客户 360°，只读）。
 *
 * <p>由近 N 天已确认（CONFIRMED）订单事实现算，<b>不落任何副本表</b>。按 (SKU, 销售单位快照) 分组，
 * 因此同 SKU 历史单位改变时分行展示、绝不把不同单位的量相加成一个「总量」。
 * {@code orderedQuantity} 是该分组各订购行的 {@code ordered_quantity} 之和，语义为**订购量**，
 * 不是实重或结算量；{@code recentUnitPrice} 复用 §7.5 口径取该分组最近一张已确认订单行的锁定单价快照
 * （锁定价异常缺失时为 {@code null}，不用草稿价 / 当前价兜底），不回算当前价格、不参与 {@code PriceResolver} 定价。
 */
@Data
public class CustomerFrequentSkuVO {

    private Long skuId;

    /**
     * 商品快照：取该分组「最近一次已确认订单行」的快照，而非任意历史行。
     */
    private String skuCode;

    private String productName;

    private String specName;

    /**
     * 销售单位快照：分组键之一，行内单位与总计无关，跨单位不做求和。
     */
    private String unit;

    /**
     * 订单次数 = 该 (SKU, 单位) 分组命中的已确认订单数（{@code COUNT(DISTINCT order_id)}）。
     */
    private Long orderCount;

    /**
     * 订购量：分组内 {@code ordered_quantity} 之和，四位定点字符串，非实重 / 结算量。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderedQuantity;

    /**
     * 最近一次确认时间（Asia/Shanghai 日界窗口内）。
     */
    private OffsetDateTime lastConfirmedAt;

    /**
     * 最近已确认订单价（§7.5 口径）：可能为 {@code null}，表示锁定单价缺失，前端不兜底。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal recentUnitPrice;
}
