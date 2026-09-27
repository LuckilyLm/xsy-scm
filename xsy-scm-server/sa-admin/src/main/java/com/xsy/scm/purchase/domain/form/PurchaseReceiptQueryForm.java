package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Size;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.purchase.constant.ScmPutawayStatusEnum;
import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;
import com.xsy.scm.purchase.constant.ScmReceiptStatusEnum;

import java.time.OffsetDateTime;

import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 采购收货单列表查询条件（W5 Target Design §7.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseReceiptQueryForm extends PageParam {
    @Size(max = 64, message = "收货单号不能超过64个字符")
    private String receiptNo;
    private Long purchaseOrderId;
    private Long supplierId;
    private Long warehouseId;
    @ScmEnumValue(enumClass = ScmReceiptStatusEnum.class, message = "收货单状态无效")
    private String status;
    @ScmEnumValue(enumClass = ScmReceiptModeEnum.class, message = "收货入库方式无效")
    private String receiptMode;
    @ScmEnumValue(enumClass = ScmPutawayStatusEnum.class, message = "上架状态无效")
    private String putawayStatus;
    private OffsetDateTime receivedFrom;
    private OffsetDateTime receivedTo;
}
