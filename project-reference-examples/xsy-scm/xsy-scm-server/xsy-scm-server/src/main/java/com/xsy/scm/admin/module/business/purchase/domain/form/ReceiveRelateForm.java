package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 无单收货 补关联采购单 表单
 *
 * @author xsy-scm
 */
@Data
public class ReceiveRelateForm {

    @Schema(description = "收货单ID")
    @NotNull(message = "收货单ID不能为空")
    private Long receiveId;

    @Schema(description = "采购单ID")
    @NotNull(message = "采购单ID不能为空")
    private Long purchaseId;

    @Schema(description = "采购明细ID")
    @NotNull(message = "采购明细ID不能为空")
    private Long itemId;
}
