package com.xsy.scm.admin.module.business.supplier.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 供应商账号 添加表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierAccountAddForm {

    @Schema(description = "供应商ID")
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    @Schema(description = "登录账号")
    @NotBlank(message = "登录账号不能为空")
    private String account;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "微信 openid")
    private String openid;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;
}
