package com.xsy.scm.admin.module.business.supplier.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 供应商账号 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierAccountQueryForm extends PageParam {

    @Schema(description = "搜索词（账号/手机号）")
    private String searchWord;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
