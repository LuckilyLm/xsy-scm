package com.xsy.scm.finance.domain.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 自动核销的候选目标（只读）：按账单日期排序，带当前未结金额。
 *
 * <p>
 * 未结金额由「应收/应付净额 - 已核销净额」现算，不读缓存列；与 {@code FinanceWriteOffService#balance} 同一口径。
 */
@Data
public class FinanceAutoWriteOffTargetDto {

    private Long targetId;

    private String targetNo;

    private String targetName;

    private OffsetDateTime eventAt;

    private BigDecimal openAmount;

    /** 应收看销售员、应付看采购员，用于数据范围过滤。 */
    private Long ownerId;
}
