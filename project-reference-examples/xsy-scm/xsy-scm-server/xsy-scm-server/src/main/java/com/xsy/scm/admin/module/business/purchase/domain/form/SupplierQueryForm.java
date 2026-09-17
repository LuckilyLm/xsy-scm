package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 供应商档案 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierQueryForm extends PageParam {

    @Schema(description = "搜索词（供应商名称）")
    private String searchWord;

    @Schema(description = "状态：1 启用，2 停用")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
