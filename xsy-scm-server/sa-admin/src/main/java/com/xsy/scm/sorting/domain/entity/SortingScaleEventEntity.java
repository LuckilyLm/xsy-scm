package com.xsy.scm.sorting.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** 电子秤事件记录原始读数；只有人工接受后才更新分拣数量，设备上报不能直接当作分拣结果。 */
@Data
@TableName(value = "xsy_sorting_scale_event")
public class SortingScaleEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventKey;

    private Long taskId;

    /**
     * 对应的任务明细。当前上报契约要求必填，避免接收时猜测属于哪个订单行。
     */
    private Long taskItemId;

    private Long skuId;

    private String deviceCode;

    /**
     * 设备原始读数（不做任何换算或修正）。
     */
    private BigDecimal rawReading;

    private String unit;

    /**
     * 设备给出的稳定判定；未稳定的读数不能作为分拣结果。
     */
    private Boolean stableFlag;

    /**
     * 设备采集时间（设备时钟不可信、可能离线补传）。
     */
    private OffsetDateTime capturedAt;

    /**
     * 服务端接收时间。
     */
    private OffsetDateTime receivedAt;

    /** {@code PENDING} / {@code ACCEPTED} / {@code REJECTED}。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;

    private BigDecimal acceptedQuantity;

    private OffsetDateTime acceptedAt;

    private String acceptedBy;

    private OffsetDateTime rejectedAt;

    private String rejectedBy;

    private String rejectReason;

    @Version
    private Integer version = 0;
}
