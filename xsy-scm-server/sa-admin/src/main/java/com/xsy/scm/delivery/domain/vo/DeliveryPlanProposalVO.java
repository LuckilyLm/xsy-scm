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

    /** 冻结建议生成时的线路版本，用于页面预警；正式应用仍由服务端重验快照。 */
    private Integer sourceRouteVersion;
    private String sourceGeomCrs;
    private BigDecimal sourceLongitude;
    private BigDecimal sourceLatitude;
    /** 排线未采用的限制条件，页面必须显式说明，不能把启发式距离当成容量/时间窗承诺。 */
    private List<String> ignoredFactors = new ArrayList<>();

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
