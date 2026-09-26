package net.lab1024.sa.admin.module.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 付款登记的结果视图：只回本次事实本身，够支撑幂等重放与调用确认。
 *
 * <p>刻意不含派生列（待核销余额、已用额、结清状态、核销行）—— 那些是 F1-5 的读时派生。
 */
@Data
public class FinancePaymentVO {

    private Long paymentId;

    private String paymentNo;

    private String counterpartyType;

    private Long counterpartyId;

    /**
     * 登记时冻结的对方名称快照，不是主档当前名。
     */
    private String counterpartyName;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    private String method;

    private OffsetDateTime paidAt;

    private String externalReference;

    private String sourceType;

    private Long sourceId;

    private String remark;

    /**
     * 本期只可能是 {@code NORMAL}；{@code REVERSE} 属 F1-3C。
     */
    private String entryType;
}
