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
 * 电子秤事件：设备事件 → 稳定读数 → 人工接受。
 *
 * <p>
 * <b>设备读数不是分拣结果</b>：本表只是把设备上报的事实记下来（设备、原始读数、单位、
 * 稳定判定、采集与接收时间），只有人工 {@code accept} 才会写进分拣结果并回填
 * {@code acceptedQuantity} / {@code acceptedBy} / {@code acceptedAt}。把「设备说多少就多少」
 * 直接写进分拣结果，等于让一台未经校准的秤决定发货数量。
 *
 * <p>
 * {@code eventKey} 唯一：同一读数重复上报只落一行，重复上报按成功返回既有记录 ——
 * 设备在弱网下会重试，把它当错误会让设备一直重试。
 */
@Data
@TableName(value = "sorting_scale_event")
public class SortingScaleEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventKey;

    private Long taskId;

    /**
     * 对应的任务明细；接受前可以为空（设备先报了读数、人再把它挂到某一行）。
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
