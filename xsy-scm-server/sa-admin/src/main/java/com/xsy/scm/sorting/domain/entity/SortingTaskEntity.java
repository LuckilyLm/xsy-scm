package com.xsy.scm.sorting.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 任务按仓库和分拣员组织订单行，占用状态保存在明细中。取消任务时必须在同一事务释放全部有效明细占用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xsy_sorting_task")
public class SortingTaskEntity extends SortingRecord {
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String taskNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String warehouseNameSnapshot;
    /**
     * 受指派分拣员；{@code null} 表示未指派，未指派任务只对持分配权的人可见。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long assigneeEmployeeId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private OffsetDateTime startedAt;
    /**
     * 最近一次完成时间；重开后保留，作为「该任务曾完成过」的痕迹。
     */
    private OffsetDateTime completedAt;
    private OffsetDateTime cancelledAt;

    /**
     * 建单时<b>冻结</b>的送货时间：筛选按它，不按订单当前值。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime deliveryTimeSnapshot;

    /**
     * 预配送波次标识；线路维度由它表达，不从订单反查线路。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String deliveryWave;

    /**
     * 建单时<b>显式指定</b>的供应商来源；不从 SKU 与供应商的多对多关系推断。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplierNameSnapshot;

    /**
     * 打印触发次数：只代表出单动作，不代表物理出纸成功，也不改任何状态。
     */
    private Integer printCount;
    private OffsetDateTime lastPrintedAt;
    private String lastPrintedBy;
}
