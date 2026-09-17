package com.xsy.scm.admin.module.business.finance.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 会计凭证 添加表单
 *
 * @author xsy-scm
 */
@Data
public class VoucherAddForm {

    @Schema(description = "凭证日期")
    @NotNull(message = "凭证日期不能为空")
    private LocalDate voucherDate;

    @Schema(description = "凭证类型：1 收款，2 付款，3 应收，4 应付，5 费用")
    @NotNull(message = "凭证类型不能为空")
    private Integer voucherType;

    @Schema(description = "关联业务类型")
    private Integer bizType;

    @Schema(description = "关联业务单ID")
    private Long bizId;

    @Schema(description = "凭证分录")
    @Valid
    @NotEmpty(message = "凭证分录不能为空")
    private List<VoucherEntryForm> entries;
}
