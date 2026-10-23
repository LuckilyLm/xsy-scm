package com.xsy.scm.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.xsy.scm.common.json.JsonbObjectMapTypeHandler;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Data;

/**
 * 路线建议记录生成时的输入、距离和站点顺序，应用前不可改写。一条线路最多保留一份待确认建议；生成新建议时废弃旧建议。
 */
@Data
@TableName(value = "xsy_delivery_plan_proposal", autoResultMap = true)
public class DeliveryPlanProposalEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long routeId;

    /**
     * {@code PROPOSED} / {@code APPLIED} / {@code DISCARDED}。
     */
    private String status;

    private String providerCode;

    private String providerVersion;

    /**
     * true = 估算（非真实路网 / 非实时交通）；必须透传到界面。
     */
    private Boolean estimatedFlag;

    private String ruleCode;

    private Integer stopCount;

    private BigDecimal totalDistance;

    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> inputSnapshot;

    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> resultSnapshot;

    private OffsetDateTime createdAt;

    private String createdBy;

    private OffsetDateTime appliedAt;

    private String appliedBy;

    private OffsetDateTime discardedAt;

    private String discardedBy;

    @Version
    private Integer version = 0;
}
