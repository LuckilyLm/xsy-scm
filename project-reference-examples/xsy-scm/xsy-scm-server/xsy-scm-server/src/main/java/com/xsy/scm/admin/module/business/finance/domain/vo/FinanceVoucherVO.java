package com.xsy.scm.admin.module.business.finance.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会计凭证 返回对象
 *
 * @author xsy-scm
 */
@Data
public class FinanceVoucherVO {

    @Schema(description = "主键ID")
    private Long voucherId;

    @Schema(description = "凭证号")
    private String voucherNo;

    @Schema(description = "凭证日期")
    private LocalDate voucherDate;

    @Schema(description = "凭证类型：1 收款，2 付款，3 应收，4 应付，5 费用")
    private Integer voucherType;

    @Schema(description = "关联业务类型")
    private Integer bizType;

    @Schema(description = "关联业务单ID")
    private Long bizId;

    @Schema(description = "借方合计（不含税）")
    private BigDecimal totalDebit;

    @Schema(description = "贷方合计（不含税）")
    private BigDecimal totalCredit;

    @Schema(description = "同步状态：1 未同步，2 同步中，3 同步成功，4 同步失败")
    private Integer syncStatus;

    @Schema(description = "外部系统单据号")
    private String externalNo;

    @Schema(description = "凭证状态：1 已生成，2 已作废")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
