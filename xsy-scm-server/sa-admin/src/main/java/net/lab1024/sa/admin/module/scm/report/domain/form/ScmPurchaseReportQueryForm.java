package net.lab1024.sa.admin.module.scm.report.domain.form;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportDateFilter;

/**
 * 采购分析页的共用筛选条件（采购概览 / 按商品 / 按供应商 / 按采购员 / 采购明细 / 价格波动）。
 *
 * <p><b>日期是「提交日期」</b>：采购事实从提交起才成立，草稿没有承诺量也没有价格事实。
 * 默认统计 {@code SUBMITTED / PARTIALLY_RECEIVED / RECEIVED / SHORT_CLOSED}，
 * 排除 {@code DRAFT} 与 {@code CANCELLED}（由 SQL 固定，不开放给调用方改写）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmPurchaseReportQueryForm extends PageParam implements ScmReportDateFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private Long supplierId;

    private Long purchaserId;

    private Long warehouseId;

    /** 单个采购状态，仅在四个「已提交」状态内进一步收窄；传其他值即查不到数据。 */
    private String status;

    /** 商品名称 / SPU 编码 / SKU 编码模糊匹配。 */
    private String keyword;

    /** 价格波动维度：按 SKU 收窄，避免把不同单位的价格画进同一条线。 */
    private Long skuId;
}
