package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 商品转换单 添加表单
 *
 * @author xsy-scm
 */
@Data
public class ProductConvertAddForm {

    @Schema(description = "转换类型：1 整件拆零，2 组合拆分")
    @NotNull(message = "转换类型不能为空")
    private Integer convertType;

    @Schema(description = "仓库ID，单仓库默认1")
    private Long warehouseId;

    @Schema(description = "来源：1 手工创建，2 发货差异表批量转换")
    private Integer sourceType;

    @Schema(description = "转换明细")
    @Valid
    @NotEmpty(message = "转换明细不能为空")
    private List<ProductConvertItemForm> items;
}
