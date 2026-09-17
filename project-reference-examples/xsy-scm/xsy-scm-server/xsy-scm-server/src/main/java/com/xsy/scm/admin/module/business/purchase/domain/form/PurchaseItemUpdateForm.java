package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 采购明细 更新表单
 *
 * @author xsy-scm
 */
@Data
public class PurchaseItemUpdateForm extends PurchaseItemAddForm {

    @Schema(description = "明细ID")
    @NotNull(message = "明细ID不能为空")
    private Long itemId;
}
