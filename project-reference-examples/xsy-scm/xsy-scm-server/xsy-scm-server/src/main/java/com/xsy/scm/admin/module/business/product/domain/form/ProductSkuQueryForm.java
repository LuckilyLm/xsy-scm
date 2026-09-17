package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 商品规格 SKU 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ProductSkuQueryForm extends PageParam {

    @Schema(description = "所属商品ID，用于定位规格")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "搜索词（规格名称）")
    private String searchWord;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
