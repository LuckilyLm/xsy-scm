package com.xsy.scm.admin.module.business.order.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 销售订单明细 更新表单
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderItemUpdateForm extends SaleOrderItemAddForm {

    @Schema(description = "明细ID")
    @NotNull(message = "明细ID不能为空")
    private Long itemId;
}
