package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 库存盘点明细 更新表单
 *
 * @author xsy-scm
 */
@Data
public class StockCheckItemUpdateForm extends StockCheckItemAddForm {

    @Schema(description = "明细ID")
    @NotNull(message = "明细ID不能为空")
    private Long itemId;
}
