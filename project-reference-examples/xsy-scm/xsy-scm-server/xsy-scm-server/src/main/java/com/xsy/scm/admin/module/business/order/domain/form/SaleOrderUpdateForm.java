package com.xsy.scm.admin.module.business.order.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 销售订单 更新表单
 *
 * @author xsy-scm
 */
@Data
public class SaleOrderUpdateForm extends SaleOrderAddForm {

    @Schema(description = "订单ID")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
}
