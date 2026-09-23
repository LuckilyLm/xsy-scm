package net.lab1024.sa.admin.module.scm.report.domain.form;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportDateFilter;

/**
 * 收货与入库页的筛选条件（收货明细 / 入库明细 / 待入库三个 Tab 共用）。
 *
 * <p><b>收货与入库是两件事</b>：收货确认（{@code purchase_receipt.status=CONFIRMED}）只是
 * 商业确认，库存入账由 {@code putaway_status=COMPLETED} 决定。因此本域的「日期」在两处含义不同：
 * 收货明细按 {@code confirmed_at}，入库明细按 {@code inventory_movement.occurred_at}。
 * 页面不得把收货确认显示成「已入库」。
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

    @Pattern(regexp = "DIRECT|WAREHOUSE_CONFIRM", message = "收货模式不合法")
    private String receiptMode;

    @Pattern(regexp = "PENDING|COMPLETED", message = "入库状态不合法")
    private String putawayStatus;
}
