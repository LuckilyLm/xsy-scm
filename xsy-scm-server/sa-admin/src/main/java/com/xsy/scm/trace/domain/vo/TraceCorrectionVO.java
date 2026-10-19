package com.xsy.scm.trace.domain.vo;

import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 包装码更正申请。
 *
 * <p>
 * 一条申请对应一条绑定；批准后追加一条解绑事实，原绑定行不动 —— 解绑不是删除，是「这条绑定不再生效」。
 */
@Data
public class TraceCorrectionVO {

    private Long id;

    private Long unitId;

    private String unitCode;

    private Long lotId;

    private String lotNo;

    private Long bindingId;

    private String customerName;

    private String orderNo;

    private String reason;

    private String status;

    private String requestedBy;

    private OffsetDateTime requestedAt;

    private String decidedBy;

    private OffsetDateTime decidedAt;

    private String decisionReason;

    private Integer version;
}
