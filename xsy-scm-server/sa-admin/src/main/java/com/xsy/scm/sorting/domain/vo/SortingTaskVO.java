package com.xsy.scm.sorting.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 分拣任务列表行。刻意<b>不</b>汇总计划量/分拣量：一个任务里的行可以来自不同单位，跨单位求和出来的数字没有业务含义。行数与「已处理行数」才是这一层可加的量。
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

    /**
     * 建单时冻结的送货时间；筛选与展示都按它，不按订单当前值。
     */
    private OffsetDateTime deliveryTimeSnapshot;

    /**
     * 预配送波次；线路维度由它表达。
     */
    private String deliveryWave;

    /**
     * 建单时显式指定的供应商来源与名称快照。
     */
    private Long supplierId;

    private String supplierNameSnapshot;

    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime cancelledAt;
    private Integer printCount;
    private OffsetDateTime lastPrintedAt;
    private Integer version;
}
