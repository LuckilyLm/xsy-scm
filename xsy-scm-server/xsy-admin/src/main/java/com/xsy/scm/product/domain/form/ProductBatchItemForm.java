package com.xsy.scm.product.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 批量命令里的一行定位：乐观锁版本随商品逐条带上，冲突时能指到具体商品。
 */
@Data
public class ProductBatchItemForm {
    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须大于0")
    private Long spuId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
