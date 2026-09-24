package net.lab1024.sa.admin.module.scm.sorting.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 分拣任务列表行。刻意**不**汇总计划量/分拣量：一个任务里的行可以来自不同单位，
 * 跨单位求和出来的数字没有业务含义。行数与「已处理行数」才是这一层可加的量。
 */
@Data
public class SortingTaskVO {
    private Long id;
    private String taskNo;
    private Long warehouseId;
    private String warehouseNameSnapshot;
    private Long assigneeEmployeeId;
    private String assigneeName;
    private String status;
    private Integer itemCount;
    private Integer processedCount;
    private String remark;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime cancelledAt;
    private Integer printCount;
    private OffsetDateTime lastPrintedAt;
    private Integer version;
}
