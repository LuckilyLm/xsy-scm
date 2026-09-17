package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 商品-供应商关系 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ProductSupplierQueryForm extends PageParam {

    @Schema(description = "商品ID，用于定位供应商关系")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "是否默认供应商")
    private Boolean defaultFlag;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
