package com.xsy.scm.admin.module.business.finance.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 凭证同步结果回执 表单
 *
 * <p>由外部对接层在推送完成后回调，置为同步成功 / 失败。</p>
 *
 * @author xsy-scm
 */
@Data
public class VoucherPushConfirmForm {

    @Schema(description = "凭证ID")
    @NotNull(message = "凭证ID不能为空")
    private Long voucherId;

    @Schema(description = "是否同步成功")
    @NotNull(message = "同步结果不能为空")
    private Boolean success;

    @Schema(description = "外部系统单据号（成功回写）")
    private String externalNo;
}
