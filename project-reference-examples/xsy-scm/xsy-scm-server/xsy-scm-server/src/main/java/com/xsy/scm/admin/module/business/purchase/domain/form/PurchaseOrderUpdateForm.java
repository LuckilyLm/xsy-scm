package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 采购单 更新表单
 *
 * @author xsy-scm
 */
@Data
public class PurchaseOrderUpdateForm extends PurchaseOrderAddForm {

    @Schema(description = "采购单ID")
    @NotNull(message = "采购单ID不能为空")
    private Long purchaseId;
}
