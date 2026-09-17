package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品价格 更新表单
 *
 * @author xsy-scm
 */
@Data
public class ProductPriceUpdateForm extends ProductPriceAddForm {

    @Schema(description = "价格ID")
    @NotNull(message = "价格ID不能为空")
    private Long priceId;
}
