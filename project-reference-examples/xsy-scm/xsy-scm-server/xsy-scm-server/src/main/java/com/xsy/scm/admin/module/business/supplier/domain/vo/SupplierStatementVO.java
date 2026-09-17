package com.xsy.scm.admin.module.business.supplier.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商对账单 返回对象
 *
 * @author xsy-scm
 */
@Data
public class SupplierStatementVO {

    @Schema(description = "主键ID")
    private Long statementId;

    @Schema(description = "对账单号")
    private String statementNo;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "对账周期开始")
    private LocalDate periodStart;

    @Schema(description = "对账周期结束")
    private LocalDate periodEnd;

    @Schema(description = "对账总金额（不含税）")
    private BigDecimal totalAmount;

    @Schema(description = "已结算金额（不含税）")
    private BigDecimal paidAmount;

    @Schema(description = "状态：1 待供应商确认，2 供应商已确认，3 已结算，4 已驳回")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
