package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 客户账期 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerPeriodQueryForm extends PageParam {

    @Schema(description = "客户ID，用于定位账期")
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @Schema(description = "账期类型：1 按金额，2 按时间")
    private Integer periodType;

    @Schema(description = "状态：1 生效，2 暂停")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
