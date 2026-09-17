package com.xsy.scm.admin.module.business.purchase.domain.form;

import com.xsy.scm.admin.module.business.purchase.constant.PurchaseStatusEnum;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 采购单 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class PurchaseOrderQueryForm extends PageParam {

    @Schema(description = "采购单号 模糊搜索")
    @Size(max = 30, message = "搜索词最多30字符")
    private String purchaseNo;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "采购员ID")
    private Long buyerId;

    @SchemaEnum(PurchaseStatusEnum.class)
    @CheckEnum(message = "采购单状态错误", value = PurchaseStatusEnum.class, required = false)
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
