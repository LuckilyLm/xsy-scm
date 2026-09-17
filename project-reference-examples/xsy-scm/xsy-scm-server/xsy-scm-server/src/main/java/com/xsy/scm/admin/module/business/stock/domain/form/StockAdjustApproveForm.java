package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 库存调整单 审核表单（审核通过 / 驳回共用）
 *
 * @author xsy-scm
 */
@Data
public class StockAdjustApproveForm {

    @Schema(description = "调整单ID")
    @NotNull(message = "调整单ID不能为空")
    private Long adjustId;

    @Schema(description = "审核意见（驳回时填写）")
    private String opinion;
}
