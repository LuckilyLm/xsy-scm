package com.xsy.scm.admin.module.business.supplier.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 供应商对账单 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class SupplierStatementQueryForm extends PageParam {

    @Schema(description = "搜索词（对账单号）")
    private String searchWord;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "状态：1 待供应商确认，2 供应商已确认，3 已结算，4 已驳回")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
