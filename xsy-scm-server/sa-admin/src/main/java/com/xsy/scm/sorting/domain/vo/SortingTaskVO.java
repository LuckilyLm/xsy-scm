package com.xsy.scm.sorting.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 分拣任务列表行。不汇总计划量和分拣量：任务里的行单位不同，跨单位求和没有意义。只给行数和已处理行数。
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

    /** 建单时保存的送货时间；筛选与展示都按它，不按订单当前值。 */
    private OffsetDateTime deliveryTimeSnapshot;

    /** 预配送波次；线路维度由它表达。 */
    private String deliveryWave;

    /** 建单时显式指定的供应商来源与名称。 */
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
