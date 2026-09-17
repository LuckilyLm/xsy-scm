package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 库存盘点单 添加表单
 *
 * @author xsy-scm
 */
@Data
public class StockCheckAddForm {

    @Schema(description = "仓库ID")
    @NotNull(message = "仓库ID不能为空")
    private Long warehouseId;

    @Schema(description = "盘点类型：1 全面盘点，2 动态盘点，3 抽盘")
    @NotNull(message = "盘点类型不能为空")
    private Integer checkType;

    @Schema(description = "状态：1 待盘点，2 盘点中，3 已提交，4 已差异处理")
    private Integer status;
}
