package com.xsy.scm.admin.module.business.order.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 销售退款单 更新表单
 *
 * @author xsy-scm
 */
@Data
public class SaleRefundUpdateForm extends SaleRefundAddForm {

    @Schema(description = "退款单ID")
    @NotNull(message = "退款单ID不能为空")
    private Long refundId;
}
