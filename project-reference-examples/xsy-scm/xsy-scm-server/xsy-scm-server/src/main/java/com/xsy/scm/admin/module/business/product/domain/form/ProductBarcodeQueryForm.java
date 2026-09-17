package com.xsy.scm.admin.module.business.product.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品条码 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ProductBarcodeQueryForm extends PageParam {

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "搜索词（条形码）")
    private String searchWord;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
