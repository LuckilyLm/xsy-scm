package com.xsy.scm.finance.dao;

import com.xsy.scm.finance.domain.dto.FinanceCreditRuleDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 只读客户与供应商账期，保存结果由财务域保存。 */
@Mapper
public interface FinanceSettlementRuleDao {
    /**
     * @param customerId
     *            非空结算主体ID；不存在返回null。 @return 当前账期；不改变客户资料。
     */
    FinanceCreditRuleDto selectCreditRule(@Param("customerId") Long customerId);

    /**
     * @param supplierId
     *            非空供应商ID；不存在返回null。 @return 当前付款天数。
     */
    Integer selectPaymentDays(@Param("supplierId") Long supplierId);
}
