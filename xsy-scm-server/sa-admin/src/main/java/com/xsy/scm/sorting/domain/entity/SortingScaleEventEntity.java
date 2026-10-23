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

/**
 * 电子秤事件记录原始读数；只有人工接受后才更新分拣数量，设备上报不能直接当作分拣结果。
 */
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

    /**
     * {@code PENDING} / {@code ACCEPTED} / {@code REJECTED}。
     */
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

    /**
     * 明细内递增的筐号；单事件上报为空。
     */
    private Integer basketNo;

    /**
     * 秤端一次人工操作的幂等号；与 {@code eventKey} 分工不同。
     */
    private String operationId;

    private Long deviceSessionId;

    private String jobVersion;

    private Integer taskItemVersion;

    /**
     * 换算后的业务单位；与明细销售单位一致才可完成。
     */
    private String businessUnit;

    /**
     * 换算后的净重；原始值与原始单位仍在 {@code rawReading} / {@code unit}。
     */
    private BigDecimal netWeight;

    /**
     * {@code NET} / {@code GROSS}；毛重不得在未去皮时直接入库。
     */
    private String weightKind;

    private String conversionRule;

    private BigDecimal tareWeight;

    /**
     * 稳定结论来源：{@code DEVICE} 硬件标记 / {@code MANUAL_CONFIRMED} 人工确认。
     */
    private String stabilitySource;

    private String protocolType;

    private String agentVersion;

    /**
     * 集合完成记录；为空表示这筐还未被完成操作覆盖。
     */
    private Long completionId;
}
