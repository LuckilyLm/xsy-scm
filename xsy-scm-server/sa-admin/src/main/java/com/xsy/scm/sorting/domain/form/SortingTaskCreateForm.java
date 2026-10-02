package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.Data;

import java.util.List;

/**
 * 建单：一次把若干**订单行**并入一个新任务。仓库由操作人显式选择，不从订单/客户/线路推断。
 *
 * <p>
 * 送货时间、波次与供应商在这里一次性冻结：任务一旦建出来，它的作业口径就不该随主档或
 * 配送线路的变化而改变。供应商必须**显式**给出 —— 从 SKU 与供应商的多对多关系反推
 * 会得到一个「可能对、也可能不对」的来源，而分拣台上的筛选是按它找货的。
 */
@Data
public class SortingTaskCreateForm {
    @NotNull(message = "仓库不能为空")
    @Positive(message = "仓库 ID 必须大于0")
    private Long warehouseId;

    /**
     * 可留空（未指派），未指派任务只有持建单与指派权的人可见。
     */
    @Positive(message = "分拣员 ID 必须大于0")
    private Long assigneeEmployeeId;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    /**
     * 送货时间（可选）；建单时冻结成快照，之后不再跟随订单变化。
     */
    private OffsetDateTime deliveryTime;

    @Size(max = 64, message = "预配送波次不能超过64个字符")
    private String deliveryWave;

    /**
     * 供应商来源（可选）；显式指定并冻结名称快照。
     */
    @Positive(message = "供应商 ID 必须大于0")
    private Long supplierId;

    @NotEmpty(message = "订单明细不能为空")
    private List<@NotNull(message = "订单明细 ID 不能为空") @Positive(message = "订单明细 ID 必须大于0") Long> salesOrderItemIds;
}
