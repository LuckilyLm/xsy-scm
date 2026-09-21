package net.lab1024.sa.admin.module.scm.product.domain.form;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 批量命令里的一行定位：乐观锁版本随商品逐条带上，冲突时能指到具体商品。
 */
@Data
public class ProductBatchItemForm {
    @NotNull
    @Positive
    private Long spuId;
    @NotNull
    @Min(0)
    private Integer version;
}
