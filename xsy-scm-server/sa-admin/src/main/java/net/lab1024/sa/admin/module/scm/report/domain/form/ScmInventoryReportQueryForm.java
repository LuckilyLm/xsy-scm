package net.lab1024.sa.admin.module.scm.report.domain.form;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportDateFilter;

/**
 * 库存分析页的筛选条件（库存流水 / 损耗分析 / 当前库存价值 / 收发存数量版）。
 *
 * <p>流水按 {@code occurred_at}，当前库存价值是<b>当前时点</b>快照、不受日期区间影响，
 * 因此价值页只借用仓库与商品筛选，忽略日期字段（VO 上必须标注「当前时点」）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmInventoryReportQueryForm extends PageParam implements ScmReportDateFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private Long warehouseId;

    private Long skuId;

    /** 商品名称 / SKU 编码模糊匹配。 */
    private String keyword;

    /** 流水类型；取值与 {@code ck_inventory_movement_type} 同源，由前端枚举提供候选。 */
    @Pattern(regexp = "PURCHASE_IN|SALES_OUT|STOCKTAKE_GAIN|STOCKTAKE_LOSS|LOSS_REPORT"
            + "|GAIN_REPORT|TRANSFER_OUT|TRANSFER_IN|CONVERT_OUT|CONVERT_IN",
            message = "流水类型不合法")
    private String movementType;

    @Pattern(regexp = "PURCHASE_RECEIPT_ITEM|SALES_OUTBOUND_ITEM|STOCKTAKE_ITEM|LOSS_GAIN_ITEM"
            + "|TRANSFER_OUT_ITEM|TRANSFER_IN_ITEM|CONVERT_OUT_ITEM|CONVERT_IN_ITEM",
            message = "来源单据类型不合法")
    private String sourceDocumentType;
}
