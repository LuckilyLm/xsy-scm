package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 库存调整单 更新表单
 *
 * @author xsy-scm
 */
@Data
public class StockAdjustUpdateForm extends StockAdjustAddForm {

    @Schema(description = "调整单ID")
    @NotNull(message = "调整单ID不能为空")
    private Long adjustId;
}
