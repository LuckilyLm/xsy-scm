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
 * 辅助排线建议（冻结快照）。
 *
 * <p>
 * 表上有触发器保证输入与结果快照不可变：建议里看到的距离必须与「应用时依据的距离」是同一份。
 * 能改快照的排线建议等于没有依据。
 *
 * <p>
 * 一条线路同时至多一条 {@code PROPOSED}（部分唯一索引兜底）：两个待确认建议会让
 * 「应用哪一个」变成猜。生成新建议时旧的待确认建议转为 {@code DISCARDED}。
 */
@Data
@TableName(value = "delivery_plan_proposal", autoResultMap = true)
public class DeliveryPlanProposalEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long routeId;

    /** {@code PROPOSED} / {@code APPLIED} / {@code DISCARDED}。 */
    private String status;

    private String providerCode;

    private String providerVersion;

    /** true = 估算（非真实路网 / 非实时交通）；必须透传到界面。 */
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
