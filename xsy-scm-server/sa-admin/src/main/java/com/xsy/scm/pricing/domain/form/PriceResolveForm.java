package com.xsy.scm.pricing.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class PriceResolveForm {
    @NotNull(message = "客户 ID 不能为空")
    private Long customerId;
    private OffsetDateTime at;
    @NotNull(message = "商品规格ID列表不能为空")
    @NotEmpty(message = "商品规格ID列表不能为空")
    @Size(max = 500, message = "试算商品规格不能超过500个")
    private List<@NotNull(message = "商品规格ID列表项不能为空") Long> skuIds;
}
