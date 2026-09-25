package net.lab1024.sa.admin.module.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 收款登记的结果视图：只回本次事实本身，够支撑幂等重放与调用确认。
 *
 * <p>刻意不含派生列（待核销余额、已核销额、结清状态）—— 那些是 F1-5 的读时派生，
 * 落进 VO 就会有人开始把它当存量字段用。
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

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    private String method;

    private OffsetDateTime receivedAt;

    private String externalReference;

    private String remark;

    /**
     * 本期只可能是 {@code NORMAL}；{@code REVERSE} 属 F1-3C。
     */
    private String entryType;
}
