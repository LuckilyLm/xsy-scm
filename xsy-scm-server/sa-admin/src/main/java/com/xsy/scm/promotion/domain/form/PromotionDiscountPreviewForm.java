package com.xsy.scm.promotion.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 优惠试算入参。
 *
 * <p>
 * 订单行只传「行 id + 基础金额」：**基础价由订单域算好后传入**，优惠域不重复实现定价。
 * 这样「协议价 → 客户类型价 → 市场价」这条顺序仍然只有一处实现。
 */
@Data
public class PromotionDiscountPreviewForm {

    @NotNull(message = "客户不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;

    /**
     * 指定活动；为空表示由服务端在生效中的活动里选（互斥组内取优先级最高）。
     */
    private Long activityId;

    /**
     * 指定客户券实例；为空表示不使用券。**不使用「自动挑一张」**：
     * 用哪张券是客户的权益，不能让系统替他决定。
     */
    private Long couponInstanceId;

    @NotEmpty(message = "订单明细不能为空")
    private List<@Valid Line> lines;

    @Data
    public static class Line {

        @NotNull(message = "订单行 ID 不能为空")
        @Positive(message = "订单行 ID 必须大于0")
        private Long orderItemId;

        /**
         * 行上的 SKU；限时特价按 SKU 命中，因此必须有它。
         */
        @NotNull(message = "商品规格不能为空")
        @Positive(message = "商品规格ID必须大于0")
        private Long skuId;

        /**
         * 行数量（下单量）；限时特价的让利 = 基础金额 − 数量 × 特价。
         */
        @NotNull(message = "数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "数量必须大于0")
        @Digits(integer = 14, fraction = 4, message = "数量最多14位整数和4位小数")
        private BigDecimal quantity;

        @NotNull(message = "基础金额不能为空")
        @DecimalMin(value = "0", message = "基础金额不能为负")
        @Digits(integer = 14, fraction = 4, message = "基础金额最多14位整数和4位小数")
        private BigDecimal baseAmount;
    }
}
