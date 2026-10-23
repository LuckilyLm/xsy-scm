package com.xsy.scm.report.domain.form;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.purchase.constant.ScmPutawayStatusEnum;
import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;
import com.xsy.scm.report.support.ScmReportDateFilter;

/**
 * 收货确认与入库是两件事：收货明细用确认时间，入库明细用库存流水时间。三类列表可以共用条件，但不能混淆状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmReceiptReportQueryForm extends PageParam implements ScmReportDateFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private Long supplierId;

    private Long warehouseId;

    private Long purchaseOrderId;

    /** 收货单号 / 采购单号 / 商品模糊匹配。 */
    private String keyword;

    @ScmEnumValue(enumClass = ScmReceiptModeEnum.class, message = "收货模式不合法")
    private String receiptMode;

    @ScmEnumValue(enumClass = ScmPutawayStatusEnum.class, message = "入库状态不合法")
    private String putawayStatus;
}
