package com.xsy.scm.delivery.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 排线建议。
 *
 * <p>
 * {@code estimated} 与 {@code providerCode} / {@code providerVersion} / {@code ruleCode} 必须一起展示：
 * 它们共同回答「这个顺序是按什么、用哪一版算法、在什么前提下算出来的」。只给一个总距离的 排线结果不可复核。
 *
 * <p>
 * {@code applied} 状态由建议自己带，线路侧不另存「当前建议」指针 —— 线路的停靠顺序本身就是 应用结果，再加一个指针会出现两者不一致。
 */
@Data
public class DeliveryPlanProposalVO {

    private Long id;

    private Long routeId;

    /** {@code PROPOSED} / {@code APPLIED} / {@code DISCARDED}。 */
    private String status;

    private String providerCode;

    private String providerVersion;

    /** true = 估算（非真实路网 / 非实时交通）。 */
    private boolean estimated;

    private String ruleCode;

    private Integer stopCount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal totalDistance;

    private List<DeliveryPlanLegVO> legs = new ArrayList<>();

    private OffsetDateTime createdAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;

    private OffsetDateTime appliedAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String appliedBy;

    private OffsetDateTime discardedAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String discardedBy;

    private Integer version;
}
