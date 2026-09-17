package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品-供应商关系 更新表单
 *
 * @author xsy-scm
 */
@Data
public class ProductSupplierUpdateForm extends ProductSupplierAddForm {

    @Schema(description = "主键ID")
    @NotNull(message = "主键ID不能为空")
    private Long id;
}
