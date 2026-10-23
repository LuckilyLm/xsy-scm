package com.xsy.scm.sorting.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 电子秤商品集合完成记录：一次人工确认对应的筐集合、总量与结果。
 *
 * <p>
 * {@code operationId} 是幂等键，重复提交返回原结果；{@code totalQuantity} 由服务端按筐重算，不用设备提交的合计。 这张表只记录「哪些筐被一起确认了」，正式分拣量仍写在任务明细上。
 */
@Data
@TableName(value = "xsy_scale_item_completion")
public class SortingScaleCompletionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operationId;

    private Long taskId;

    private Long taskItemId;

    private Long deviceId;

    private Long deviceSessionId;

    private Long employeeId;

    private Integer basketCount;

    private BigDecimal totalQuantity;

    private String businessUnit;

    /** 设备提交的有序事件键集合摘要；服务端复算一致才允许完成。 */
    private String manifestHash;

    private String result;

    private String reason;

    /** 确认前该明细的已分拣量，用于核对差额。 */
    private BigDecimal quantityBefore;

    private OffsetDateTime createdAt;

    private String createdBy;
}
