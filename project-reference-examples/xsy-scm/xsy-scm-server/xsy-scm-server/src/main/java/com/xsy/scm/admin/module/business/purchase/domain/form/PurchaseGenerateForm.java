package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采购单生成表单（按时间段订单汇总，Q4）
 *
 * <p>人工点「生成采购单」，按固定时间段订单汇总生成采购单；
 * calculateStock=true 时按现有库存实时抵扣（采购量 = 需求量 - 库存，下限为 0）。</p>
 *
 * @author xsy-scm
 */
@Data
public class PurchaseGenerateForm {

    @Schema(description = "统计开始时间（订单创建时间）")
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @Schema(description = "统计结束时间（订单创建时间）")
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @Schema(description = "是否按现有库存抵扣：true 时采购量=需求量-库存，默认 false")
    private Boolean calculateStock;

    @Schema(description = "供应商分拣模式：1 默认供应商，2 采购单生成后，3 按采购任务实时分配；缺省 1")
    private Integer supplierSortMode;
}
