package com.xsy.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.math.BigDecimal;

@Data
public class DeliveryReorderForm {
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @NotEmpty(message = "请提供完整的停靠点顺序")
    @Size(max = 500, message = "停靠点数量不能超过500个")
    private List<@NotNull(message = "停靠点编号不能为空") @Positive(message = "停靠点编号必须为正数") Long> stopIds;
}
