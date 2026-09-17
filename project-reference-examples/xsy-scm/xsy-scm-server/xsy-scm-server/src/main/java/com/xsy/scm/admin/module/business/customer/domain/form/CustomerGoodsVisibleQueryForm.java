package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 客户商品可见性 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerGoodsVisibleQueryForm extends PageParam {

    @Schema(description = "客户ID，用于定位可见性")
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "可见性：1 显示，2 屏蔽")
    private Integer visibleType;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
