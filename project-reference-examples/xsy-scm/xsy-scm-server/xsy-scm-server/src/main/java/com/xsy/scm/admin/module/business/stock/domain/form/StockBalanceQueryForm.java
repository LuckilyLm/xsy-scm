package com.xsy.scm.admin.module.business.stock.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 库存余额 分页查询表单（只读）
 *
 * @author xsy-scm
 */
@Data
public class StockBalanceQueryForm extends PageParam {

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
