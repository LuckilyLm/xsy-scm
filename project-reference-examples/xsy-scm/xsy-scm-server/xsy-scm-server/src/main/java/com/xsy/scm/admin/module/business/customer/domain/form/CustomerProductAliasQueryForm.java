package com.xsy.scm.admin.module.business.customer.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 客户商品别名 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerProductAliasQueryForm extends PageParam {

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "搜索词（别名）")
    private String searchWord;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
