package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品转换单 审核表单
 *
 * @author xsy-scm
 */
@Data
public class ProductConvertApproveForm {

    @Schema(description = "转换单ID")
    @NotNull(message = "转换单ID不能为空")
    private Long convertId;
}
