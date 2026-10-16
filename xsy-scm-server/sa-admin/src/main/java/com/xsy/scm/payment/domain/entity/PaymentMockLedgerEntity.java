package com.xsy.scm.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/** 模拟渠道账本独立追加保存，用于对账差异测试；不允许编辑或删除历史渠道事实。 */
@Data
@TableName("xsy_payment_mock_ledger")
public class PaymentMockLedgerEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String provider;

    private String providerTransactionNo;

    private String providerRefundNo;

    /** {@code IN}（收）/ {@code OUT}（退）。 */
    private String direction;

    private BigDecimal amount;

    private LocalDate bizDate;

    private OffsetDateTime occurredAt;

    private OffsetDateTime createdAt;

    private String createdBy;
}
