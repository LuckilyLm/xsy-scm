package com.xsy.scm.promotion.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 优惠冻结（确认下单）。
 *
 * <p>
 * 与试算入参同形，多一个订单 id：试算**不占用**券、不写任何表；只有这里才占用券并冻结优惠。
 * 服务端会重验活动版本、券状态与适用范围 —— 试算之后活动可能已被停用或改版。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromotionDiscountConfirmForm extends PromotionDiscountPreviewForm {

    @NotNull(message = "订单不能为空")
    @Positive(message = "订单 ID 必须大于0")
    private Long salesOrderId;
}
