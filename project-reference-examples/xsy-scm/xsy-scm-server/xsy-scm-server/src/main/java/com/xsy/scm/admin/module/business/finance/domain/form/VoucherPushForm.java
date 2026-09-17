package com.xsy.scm.admin.module.business.finance.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 凭证推送 表单
 *
 * @author xsy-scm
 */
@Data
public class VoucherPushForm {

    @Schema(description = "凭证ID")
    @NotNull(message = "凭证ID不能为空")
    private Long voucherId;

    @Schema(description = "目标外部系统类型：1 金蝶云星空，2 金蝶云星瀚，3 用友T+，4 用友U8")
    @NotNull(message = "目标外部系统不能为空")
    private Integer systemType;
}
