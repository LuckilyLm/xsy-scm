package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 库存盘点单 更新表单
 *
 * @author xsy-scm
 */
@Data
public class StockCheckUpdateForm extends StockCheckAddForm {

    @Schema(description = "盘点单ID")
    @NotNull(message = "盘点单ID不能为空")
    private Long checkId;
}
