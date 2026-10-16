package com.xsy.scm.delivery.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 配送路线建议保留算法来源、版本和是否估算的信息，方便核对。线路停靠顺序本身就是建议应用后的结果。 */
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
