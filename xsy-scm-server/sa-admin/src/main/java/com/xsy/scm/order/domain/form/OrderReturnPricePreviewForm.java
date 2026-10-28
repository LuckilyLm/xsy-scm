package com.xsy.scm.order.domain.form;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 独立退货建单前的取价预览：只读，不建单、不锁价。
 */
@Data
public class OrderReturnPricePreviewForm {
    @NotNull(message = "客户不能为空")
    private Long customerId;
    @NotEmpty(message = "请选择商品")
    @Size(max = 500, message = "单次最多预览500个商品")
    private List<Long> skuIds;
}
