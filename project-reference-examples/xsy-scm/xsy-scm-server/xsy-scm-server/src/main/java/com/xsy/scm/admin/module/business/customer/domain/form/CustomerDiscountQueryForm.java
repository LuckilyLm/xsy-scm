package com.xsy.scm.admin.module.business.customer.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 客户折扣率 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerDiscountQueryForm extends PageParam {

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "折扣范围：1 统一折扣，2 按商品，3 按分类")
    private Integer scopeType;

    @Schema(description = "状态：1 生效，2 停用")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
