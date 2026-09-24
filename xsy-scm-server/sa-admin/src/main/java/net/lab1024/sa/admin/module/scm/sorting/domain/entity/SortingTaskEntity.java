package net.lab1024.sa.admin.module.scm.sorting.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * 分拣任务：某仓库内派给某位分拣员的一批订单行。
 *
 * <p>任务状态即「是否占用订单行」的聚合答案，但**占用位写在明细行上**
 * （{@code occupation_status}），因为部分唯一索引不能跨表判断任务状态；
 * 代价是取消任务必须在同一事务里把该任务全部明细置为 {@code RELEASED}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sorting_task")
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
     * 打印触发次数：只代表出单动作，不代表物理出纸成功，也不改任何状态。
     */
    private Integer printCount;
    private OffsetDateTime lastPrintedAt;
    private String lastPrintedBy;
}
