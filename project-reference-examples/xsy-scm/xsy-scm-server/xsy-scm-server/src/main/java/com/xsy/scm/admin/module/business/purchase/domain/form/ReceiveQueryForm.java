package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 采购收货单 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ReceiveQueryForm extends PageParam {

    @Schema(description = "收货类型：1 采购收货，2 无单收货")
    private Integer receiveType;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "采购单ID")
    private Long purchaseId;

    @Schema(description = "采购明细ID")
    private Long itemId;

    @Schema(description = "状态：1 已收，2 已入库，3 已作废")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
