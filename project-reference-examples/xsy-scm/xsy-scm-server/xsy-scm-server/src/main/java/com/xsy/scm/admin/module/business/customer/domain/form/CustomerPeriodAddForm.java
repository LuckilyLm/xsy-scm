package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户账期 添加表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerPeriodAddForm {

    @Schema(description = "客户ID")
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @Schema(description = "账期类型：1 按金额，2 按时间")
    @NotNull(message = "账期类型不能为空")
    private Integer periodType;

    @Schema(description = "金额阈值（不含税），按金额时使用")
    private BigDecimal amountThreshold;

    @Schema(description = "账期值，按时间时使用")
    private Integer periodValue;

    @Schema(description = "账期单位：1 天，2 月")
    private Integer periodUnit;

    @Schema(description = "固定结算日，按月时使用")
    private Integer settleDay;

    @Schema(description = "状态：1 生效，2 暂停")
    private Integer status;
}
