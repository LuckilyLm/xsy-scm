package com.xsy.scm.admin.module.business.supplier.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 供应商商品提报 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierProductApplyQueryForm extends PageParam {

    @Schema(description = "搜索词（商品名称/别名）")
    private String searchWord;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "审核状态：1 待审核，2 已通过，3 已驳回")
    private Integer auditStatus;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
