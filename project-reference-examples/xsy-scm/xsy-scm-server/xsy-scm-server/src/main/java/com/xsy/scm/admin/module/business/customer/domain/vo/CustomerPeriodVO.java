package com.xsy.scm.admin.module.business.customer.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户账期 返回对象
 *
 * @author xsy-scm
 */
@Data
public class CustomerPeriodVO {

    @Schema(description = "账期ID")
    private Long periodId;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "账期类型：1 按金额，2 按时间")
    private Integer periodType;

    @Schema(description = "金额阈值（不含税）")
    private BigDecimal amountThreshold;

    @Schema(description = "账期值")
    private Integer periodValue;

    @Schema(description = "账期单位：1 天，2 月")
    private Integer periodUnit;

    @Schema(description = "固定结算日")
    private Integer settleDay;

    @Schema(description = "状态：1 生效，2 暂停")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
