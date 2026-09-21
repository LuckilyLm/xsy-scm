package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 采购需求列表查询条件（W5 Target Design §7.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseDemandQueryForm extends PageParam {
    @Size(max = 64)
    private String salesOrderNo;
    private Long skuId;
    private Long supplierId;
    private Long warehouseId;
    @Pattern(regexp = "PENDING|PARTIALLY_ALLOCATED|ALLOCATED")
    private String status;
    /**
     * 需求日期区间（含端点）。Q6a：demand_date 来自 source_confirmed_at 的 Asia/Shanghai 日期。
     */
    private LocalDate demandDateFrom;
    private LocalDate demandDateTo;
}
