package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

import lombok.Data;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.sorting.constant.ScmSortingTaskStatusEnum;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 分拣任务列表（按客户订单视角）。范围永远是「授权仓 ∩ 可见指派人」，这里的筛选条件只能在范围内再收窄，不能放宽。
 */
@Data
public class SortingTaskQueryForm extends PageParam {
    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    @ScmEnumValue(enumClass = ScmSortingTaskStatusEnum.class, message = "分拣任务状态无效")
    private String status;

    /**
     * 一次取多个作业中状态；非空时优先于 {@code status}。
     *
     * <p>
     * 秤端作业队列要同时看到「待分拣」和「分拣中」，逐个状态查会把分页条件拆散。
     */
    private List<String> statusList;

    @Positive(message = "仓库 ID 必须大于0")
    private Long warehouseId;

    /** 仅对持建单与指派权的人有意义：他们能跨指派人看，才可以按人筛。 */
    @Positive(message = "分拣员 ID 必须大于0")
    private Long assigneeEmployeeId;

    /** {@code true} 只看未指派任务（待办队列）。 */
    private Boolean unassignedOnly;

    /** 送货时间区间（半开区间 {@code [from, to)}），按建单时保存的时间过滤。 */
    private java.time.OffsetDateTime deliveryTimeFrom;

    private java.time.OffsetDateTime deliveryTimeTo;

    /** 预配送波次（精确）；线路维度由它表达。 */
    @Size(max = 64, message = "预配送波次不能超过64个字符")
    private String deliveryWave;

    /** 供应商来源（精确），按任务上保存的供应商过滤。 */
    @Positive(message = "供应商 ID 必须大于0")
    private Long supplierId;
}
