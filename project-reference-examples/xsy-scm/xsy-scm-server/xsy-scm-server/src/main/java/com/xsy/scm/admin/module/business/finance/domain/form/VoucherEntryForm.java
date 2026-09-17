package com.xsy.scm.admin.module.business.finance.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 凭证分录 表单
 *
 * @author xsy-scm
 */
@Data
public class VoucherEntryForm {

    @Schema(description = "会计科目编码")
    @NotBlank(message = "会计科目编码不能为空")
    private String subjectCode;

    @Schema(description = "会计科目名称")
    private String subjectName;

    @Schema(description = "借贷方向：1 借，2 贷")
    @NotNull(message = "借贷方向不能为空")
    private Integer direction;

    @Schema(description = "金额（不含税）")
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;
}
