package com.xsy.scm.delivery.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "xsy_delivery_route_order", autoResultMap = true)
public class DeliveryRouteOrderEntity extends DeliveryRecord {
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long routeId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long stopId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNoSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal orderAmountSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime expectDeliveryTimeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String assignmentStatus;
    /** 生成打印预览 / 打印任务的历史次数；不代表物理出纸，也不代表当前内容版本已打印。 */
    private Integer printCount;
    /** 最后一次生成打印的时间；{@code null} 表示从未生成。 */
    private OffsetDateTime lastPrintedAt;
    private String lastPrintedBy;
    /**
     * 订单级履约状态：{@code PENDING → IN_TRANSIT → SIGNED | EXCEPTION}。
     *
     * <p>
     * 和 {@code assignmentStatus} 相互独立：一个说货到没到手，一个说这单还在不在线路上。不用 RELEASED 表示签收——取消线路也会写 RELEASED，和拒收是两回事。
     */
    private String fulfillmentStatus;
    /** 签收或异常登记时点，服务端写入；终态必填（数据库 CHECK）。 */
    private OffsetDateTime signedAt;
    private String signedBy;
    /** 异常签收必填原因；正常签收可留空备注。 */
    private String signReason;
}
