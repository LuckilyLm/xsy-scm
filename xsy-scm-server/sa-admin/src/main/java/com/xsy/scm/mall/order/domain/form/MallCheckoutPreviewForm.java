package com.xsy.scm.mall.order.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class MallCheckoutPreviewForm {
    @Valid
    @NotEmpty(message = "请选择要结算的商品")
    private List<MallCheckoutItemForm> items;
    private Long addressId;
}
