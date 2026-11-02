package com.xsy.scm.report.domain.vo;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ScmCustomerMonthlyProfitVO extends ScmFinanceProfitRowVO {
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal taxAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal serviceAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal freightAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal commissionAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal expenseAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal netProfit;

    /**
     * 对账口径：外部对账金额 = 当月应收净额 + 有效过账金额。过账不生成应收，但对账单和发票包含它。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal statementAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal paidAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal unpaidAmount;
    /** 暂无销售发票台账，首版按对账金额暂记。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal invoiceAmount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal postingAmount;
    /** 有效过账按登记税点折算的税金。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal postingTaxAmount;
    /** 实际货款 = 对账金额 − 过账金额 − 过账税金。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal actualGoodsAmount;
    /** 对账金额 × 1%，只作为报表派生列。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal invoiceTaxAmount;
    /** 对账金额 × 6%，只作为报表派生列。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal serviceFeeAmount;
    /** 对账金额 − 成本 − 1% 税 − 6% 服务费 − 运费 − 扣点；成本缺失时为空。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal statementProfit;
}
