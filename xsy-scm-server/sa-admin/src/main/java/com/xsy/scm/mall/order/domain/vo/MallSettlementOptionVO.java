package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 结算页可选的结算方式及其实时可用性。
 *
 * <p>
 * {@code enabled=false} 只代表「此刻按当前配置不满足」，不代表永久不可用； {@code disabledReason} 给出可读原因，客户端据此置灰并提示。真正下单与确认时服务端会再校验一次，
 * 因此这个投影是「给客户看的」，不是授权依据。
 *
 * <p>
 * {@code amountVisible} 与既有共享资金可见性规则同口径（见 {@code CustomerValidator.sharedSettlement}）：集团共享结算下金额一律不返回，
 * 客户端只能展示标签与原因，不得展示或推断金额。
 */
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
