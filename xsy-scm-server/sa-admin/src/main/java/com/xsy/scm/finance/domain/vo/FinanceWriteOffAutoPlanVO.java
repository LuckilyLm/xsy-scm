package com.xsy.scm.finance.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import lombok.Data;

/**
 * 自动核销的分配预览：一行 = 一张将被核销的单据。预览不写库，实际执行前必须让人看到顺序与金额。
 */
@Data
public class FinanceWriteOffAutoPlanVO {

    private Long targetId;

    private String targetNo;

    private String targetName;

    /** 账单日期（应收/应付的 event_at），排序依据。 */
    private OffsetDateTime eventAt;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal openAmount;

    /** 本次将核销的金额。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal allocatedAmount;
}
