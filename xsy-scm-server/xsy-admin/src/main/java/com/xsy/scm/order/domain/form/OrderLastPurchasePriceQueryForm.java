package com.xsy.scm.order.domain.form;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 录单参考的最近采购价查询：一次最多 200 个商品规格，避免把整库规格塞进一次查询。
 */
@Data
public class OrderLastPurchasePriceQueryForm {

    @NotEmpty(message = "商品规格不能为空")
    @Size(max = 200, message = "一次最多查询200个商品规格")
    private List<Long> skuIds;
}
