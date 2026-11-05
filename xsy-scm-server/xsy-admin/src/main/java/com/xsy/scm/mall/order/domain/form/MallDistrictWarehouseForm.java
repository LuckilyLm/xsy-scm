package com.xsy.scm.mall.order.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class MallDistrictWarehouseForm {
    @NotNull(message = "区县编码不能为空")
    @Positive(message = "区县编码必须大于0")
    private Integer districtCode;

    @NotNull(message = "配送仓不能为空")
    @Positive(message = "配送仓必须大于0")
    private Long warehouseId;

    @NotNull(message = "优先级不能为空")
    @Min(value = 0, message = "优先级不能小于0")
    private Integer priority = 0;
}
