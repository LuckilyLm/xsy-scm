package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商档案 返回对象
 *
 * @author xsy-scm
 */
@Data
public class SupplierVO {

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "供应商编码")
    private String supplierNo;

    @Schema(description = "供应商名称")
    private String supplierName;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
