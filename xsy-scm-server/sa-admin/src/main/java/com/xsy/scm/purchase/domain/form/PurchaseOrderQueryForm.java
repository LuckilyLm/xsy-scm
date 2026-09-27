package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Size;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;

import java.time.OffsetDateTime;

import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 采购单列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrderQueryForm extends PageParam {
    @Size(max = 64, message = "采购单号不能超过64个字符")
    private String orderNo;
    private Long supplierId;
    private Long purchaserId;
    private Long warehouseId;
    @ScmEnumValue(enumClass = ScmPurchaseStatusEnum.class, message = "采购单状态无效")
    private String status;
    private OffsetDateTime createdFrom;
    private OffsetDateTime createdTo;
}
