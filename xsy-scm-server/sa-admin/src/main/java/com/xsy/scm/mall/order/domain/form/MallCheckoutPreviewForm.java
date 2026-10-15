package com.xsy.scm.mall.order.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Data;

@Data
public class MallCheckoutPreviewForm {
    @Valid
    @NotEmpty(message = "请选择要结算的商品")
    private List<MallCheckoutItemForm> items;
    private Long addressId;
    /** 客户选择试算的券实例；预览只试算不占用，正式占用在订单确认时完成 */
    @Positive(message = "券实例 ID 必须大于0")
    private Long couponInstanceId;
}
