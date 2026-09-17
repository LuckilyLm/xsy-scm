package com.xsy.scm.admin.module.business.supplier.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 供应商商品提报 更新表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierProductApplyUpdateForm extends SupplierProductApplyAddForm {

    @Schema(description = "主键ID")
    @NotNull(message = "主键ID不能为空")
    private Long applyId;
}
