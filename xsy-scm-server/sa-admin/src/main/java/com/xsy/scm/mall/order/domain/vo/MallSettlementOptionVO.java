package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Data;

/** 结算页返回当前可用的支付方式及不可用原因。是否可用需要根据实时配置和余额重新判断。 */
@Data
public class MallSettlementOptionVO {

    @Schema(description = "结算方式：WALLET / CREDIT / COD")
    private String method;

    @Schema(description = "结算方式名称")
    private String label;

    @Schema(description = "当前是否可选")
    private Boolean enabled = Boolean.FALSE;

    @Schema(description = "不可选原因（可选时为空）")
    private String disabledReason;

    /**
     * 该方式当前可用于本单的金额口径：余额为钱包可用余额，账期为<b>剩余可用授信额度</b>； 货到付款为交付时按实际交付金额收款，故为空。共享结算下恒为空（{@code amountVisible=false}）。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    @Schema(description = "该方式当前可用金额；共享结算或货到付款时为空")
    private BigDecimal availableAmount;

    @Schema(description = "是否允许返回金额（共享结算下为 false）")
    private Boolean amountVisible = Boolean.TRUE;

    @Schema(description = "补充说明")
    private String note;
}
