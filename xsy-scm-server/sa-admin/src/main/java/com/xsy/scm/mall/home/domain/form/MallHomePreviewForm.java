package com.xsy.scm.mall.home.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class MallHomePreviewForm {
    @NotNull(message = "预览客户不能为空")
    @Positive(message = "预览客户必须大于0")
    private Long customerId;
    @NotNull(message = "预览配送仓不能为空")
    @Positive(message = "预览配送仓必须大于0")
    private Long warehouseId;
}
