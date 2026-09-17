package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 采购单 改绑供应商 表单
 *
 * <p>当前采购单为「一单一供应商」模型，改绑按整单生效：同步更新采购单与全部明细的绑定供应商。</p>
 *
 * @author xsy-scm
 */
@Data
public class PurchaseItemAssignSupplierForm {

    @Schema(description = "采购单ID")
    @NotNull(message = "采购单ID不能为空")
    private Long purchaseId;

    @Schema(description = "供应商ID")
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;
}
