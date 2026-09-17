package com.xsy.scm.admin.module.business.finance.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 凭证分录 返回对象
 *
 * @author xsy-scm
 */
@Data
public class VoucherEntryVO {

    @Schema(description = "主键ID")
    private Long entryId;

    @Schema(description = "凭证ID")
    private Long voucherId;

    @Schema(description = "会计科目编码")
    private String subjectCode;

    @Schema(description = "会计科目名称")
    private String subjectName;

    @Schema(description = "借贷方向：1 借，2 贷")
    private Integer direction;

    @Schema(description = "金额（不含税）")
    private BigDecimal amount;
}
