package com.xsy.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;

/**
 * 已生成的对账单版本；下载时不重新计算金额和明细。
 */
@Data
public class ScmCustomerStatementVO {
    private Long id;
    private Long settlementCustomerId;
    private String settlementCustomerName;
    private Long customerId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean partialScope;
    private Long generatedByEmployeeId;
    private OffsetDateTime generatedAt;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal openingReceivable;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receivableIncrease;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receivableRed;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal writeOffNet;
    /**
     * 本期抹零净额：抹零没有资金流入，不能并进核销净额，否则「未分配资金」会被少算。
     *
     * <p>
     * 不落表：对账单明细已经冻结，读时从明细事实汇总与冻结结果等价。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal waiverNet;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal closingReceivable;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal openingUnallocated;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal receiptNet;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal closingUnallocated;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal refundNet;
    private List<ScmCustomerStatementItemVO> items;
}
