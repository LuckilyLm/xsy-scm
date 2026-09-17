package com.xsy.scm.admin.module.business.finance.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票 返回对象
 *
 * @author xsy-scm
 */
@Data
public class InvoiceVO {

    @Schema(description = "主键ID")
    private Long invoiceId;

    @Schema(description = "发票号")
    private String invoiceNo;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "关联发票号（多张时逗号分隔）")
    private String relateInvoiceNos;

    @Schema(description = "价税合计")
    private BigDecimal amount;

    @Schema(description = "税率")
    private BigDecimal taxRate;

    @Schema(description = "状态：1 可开票，2 已开票，3 已红冲")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
