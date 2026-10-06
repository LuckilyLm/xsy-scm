package com.xsy.scm.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 本地模拟渠道<b>自己的账</b>（仅 MOCK 渠道使用）。
 *
 * <p>
 * 存在的理由是让对账能真的对出差异：如果模拟渠道的账就是本地交易表的投影， 它永远平账，差异路径不可测。
 *
 * <p>
 * 这张表刻意没有 {@code deleted} / {@code version} / {@code updated_*}： 渠道的账本只有追加，结构上就没有改一行的入口。
 */
@Data
@TableName("payment_mock_ledger")
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
