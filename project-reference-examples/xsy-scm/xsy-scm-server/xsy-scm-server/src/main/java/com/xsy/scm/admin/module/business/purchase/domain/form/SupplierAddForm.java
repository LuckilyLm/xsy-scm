package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 供应商档案 添加表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierAddForm {

    @Schema(description = "供应商名称")
    @NotBlank(message = "供应商名称不能为空")
    private String supplierName;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;
}
