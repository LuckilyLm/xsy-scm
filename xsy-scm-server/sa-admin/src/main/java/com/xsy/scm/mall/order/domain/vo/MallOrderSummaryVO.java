package com.xsy.scm.mall.order.domain.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 商城「下单商品汇总」：把客户自己的历史订单按商品规格聚合成一行。
 *
 * <p>
 * 口径是下单事实：数量取订单行下单数量、金额取订单行下单金额，已取消与未提交的订单不计入。 售后与退货不回冲这里的数量，退货是另一条事实，混进来会让「买过多少」和「现在还剩多少」都说不清。
 */
@Data
public class MallOrderSummaryVO {

    private List<Item> items;

    /** 整个区间的合计，不受分页影响。 */
    private Totals totals;

    private Long pageNum;
    private Long pageSize;
    private Long total;
    private Long pages;
    private Boolean emptyFlag;

    /** 单个商品规格的汇总行。 */
    @Data
    public static class Item {
        private Long skuId;
        private String productName;
        private String specName;
        private String saleUnit;
        /** 下单数量合计。 */
        private BigDecimal quantity;
        /** 下单金额合计。 */
        private BigDecimal amount;
        /** 出现在多少张订单里。 */
        private Long orderCount;
        private String imageUrl;
    }

    /** 区间合计。 */
    @Data
    public static class Totals {
        private BigDecimal quantity;
        private BigDecimal amount;
        private Long orderCount;
        private Long skuCount;
    }
}
