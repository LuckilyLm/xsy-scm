package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 收款命令的结果视图：只回本次事实本身，够支撑幂等重放与调用确认。
 *
 * <p>
 * 刻意不含派生列（待核销余额、已核销额、结清状态）—— 那些是的读时派生，落进 VO 就会有人开始把它当存量字段用。
 */
@Data
public class FinanceReceiptVO {

    private Long receiptId;

    private String receiptNo;

    private Long customerId;

    /**
     * 登记时冻结的客户名称快照，不是主档当前名。
     */
    private String customerName;
    private Long settlementCustomerId;
    private String settlementCustomerName;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    private String method;

    private OffsetDateTime receivedAt;

    private String externalReference;

    private String remark;

    /**
     * {@code NORMAL} 收款或 {@code REVERSE} 反向收款。
     */
    private String entryType;

    private Long reverseOfId;

    private String reason;
}
