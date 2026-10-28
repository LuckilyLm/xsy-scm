package com.xsy.scm.finance.domain.form;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 过账列表、详情与导出共用筛选条件（按过账业务日期闭区间）。
 *
 * <p>
 * 这是现场口径里「内部财务报表可单独筛选查看所有过账记录」的落点： 列表本身就是完整的过账台账，不再另做一张口径可能不同的报表页。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinancePostingQueryForm extends FinanceDatePageForm {

    @Positive(message = "过账记录编号必须大于0")
    private Long postingId;

    @Positive(message = "客户编号必须大于0")
    private Long customerId;

    /**
     * 单号 / 客户名称模糊匹配。
     */
    private String keyword;

    /**
     * {@code PENDING / PARTIAL / REFUNDED}。
     */
    private String refundStatus;

    /**
     * {@code NORMAL} 只看有效，{@code VOIDED} 只看已作废，不填全部。
     */
    private String status;
}
