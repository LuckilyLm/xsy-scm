package com.xsy.scm.balance.domain.form;

import java.time.OffsetDateTime;
import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/** 余额流水查询。 */
@Data
public class BalanceMovementQueryForm extends PageParam {

    /** 按钱包所有者（结算主体）收窄。 */
    private Long settlementCustomerId;

    /** 按本次业务客户收窄（集团场景下定位「哪家下属单位花的」）。 */
    private Long customerId;

    /** {@code RECHARGE} / {@code CONSUME} / {@code REFUND} / {@code CORRECTION}。 */
    private String type;

    /** {@code CREDIT} / {@code DEBIT}。 */
    private String direction;

    private OffsetDateTime occurredFrom;

    private OffsetDateTime occurredTo;
}
