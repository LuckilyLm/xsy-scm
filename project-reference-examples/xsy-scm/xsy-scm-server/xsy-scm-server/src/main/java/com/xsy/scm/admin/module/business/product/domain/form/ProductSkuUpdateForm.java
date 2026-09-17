package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品规格 SKU 更新表单
 *
 * @author xsy-scm
 */
@Data
public class ProductSkuUpdateForm extends ProductSkuAddForm {

    @Schema(description = "SKU ID")
    @NotNull(message = "SKU ID不能为空")
    private Long skuId;
}
