package com.xsy.scm.mall.cart.domain.form;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

import lombok.Data;

@Data
public class MallCartEstimateForm {
    @NotEmpty(message = "请选择要预估的商品")
    private List<Long> skuIds;
}
