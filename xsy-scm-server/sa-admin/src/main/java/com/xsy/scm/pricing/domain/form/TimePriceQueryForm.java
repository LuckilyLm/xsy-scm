package com.xsy.scm.pricing.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 时价列表查询。
 */
@Data
public class TimePriceQueryForm {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于0")
    private Long pageNum;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页最多100条")
    private Long pageSize;

    /**
     * 商品名称 / 规格编码模糊匹配。
     */
    private String keyword;

    private Long skuId;

    /**
     * 只看与这个时刻有交集的价格；留空表示全部。
     */
    private OffsetDateTime at;
}
