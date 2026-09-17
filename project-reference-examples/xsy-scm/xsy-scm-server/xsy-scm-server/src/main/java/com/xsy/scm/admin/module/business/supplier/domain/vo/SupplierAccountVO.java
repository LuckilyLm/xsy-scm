package com.xsy.scm.admin.module.business.supplier.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商账号 返回对象
 *
 * @author xsy-scm
 */
@Data
public class SupplierAccountVO {

    @Schema(description = "主键ID")
    private Long accountId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "登录账号")
    private String account;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
