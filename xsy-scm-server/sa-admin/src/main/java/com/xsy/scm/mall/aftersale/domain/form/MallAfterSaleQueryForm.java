package com.xsy.scm.mall.aftersale.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MallAfterSaleQueryForm {
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于0")
    private Long pageNum;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页最多100条")
    private Long pageSize;

    /** 只看某一订单的售后；留空为全部 */
    private Long orderId;
}
