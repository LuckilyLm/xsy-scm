package com.xsy.scm.mall.funds.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.constant.ScmCurrencyEnum;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class MallWalletSummaryVO {
    private String currency = ScmCurrencyEnum.CNY.name();
    private Boolean balanceVisible;
    private Boolean hasWallet;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal availableBalance;
}
