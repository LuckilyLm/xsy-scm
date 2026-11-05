package com.xsy.scm.finance.service;

import com.xsy.scm.common.constant.ScmCreditPeriodTypeEnum;
import com.xsy.scm.common.constant.ScmCreditPeriodUnitEnum;
import com.xsy.scm.finance.constant.ScmFinanceEntryTypeEnum;
import com.xsy.scm.finance.dao.FinancePayableDao;
import com.xsy.scm.finance.dao.FinanceSettlementRuleDao;
import com.xsy.scm.finance.domain.entity.FinancePayableEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 在追加财务事实前冻结账期，后续客户或供应商改档不影响历史应收应付。 */
@Service
@RequiredArgsConstructor
public class FinanceSettlementRuleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final FinanceSettlementRuleDao ruleDao;
    private final FinancePayableDao payableDao;

    /**
     * 冻结正常应收的信用规则；独立红字无原单，关联红字由生成器继承原快照。
     *
     * @param receivable
     *            非空、已设置结算主体与真实业务时间的正常应收；规则缺失时拒绝入账。
     */
    public void freezeReceivable(FinanceReceivableEntity receivable) {
        var rule = ruleDao.selectCreditRule(receivable.getSettlementCustomerId());
        if (rule == null || receivable.getEventAt() == null) {
            throw new IllegalStateException("应收缺少结算主体账期或业务时间");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", rule.getType());
        snapshot.put("value", rule.getValue());
        snapshot.put("unit", rule.getUnit());
        snapshot.put("settleDay", rule.getSettleDay());
        snapshot.put("amountThreshold", rule.getAmountThreshold());
        receivable.setCreditRuleSnapshot(snapshot);
        LocalDate base = receivable.getEventAt().atZoneSameInstant(BUSINESS_ZONE).toLocalDate();
        LocalDate due = null;
        if (ScmCreditPeriodTypeEnum.BY_TIME.name().equals(rule.getType())) {
            if (rule.getValue() == null || rule.getValue() <= 0) {
                throw new IllegalStateException("客户账期长度无效");
            }
            if (ScmCreditPeriodUnitEnum.DAY.name().equals(rule.getUnit())) {
                due = base.plusDays(rule.getValue());
            } else if (ScmCreditPeriodUnitEnum.MONTH.name().equals(rule.getUnit())) {
                // 月结按目标月首日加约定结算日计算，不能按原日期直接加月份。
                due = base.withDayOfMonth(1).plusMonths(rule.getValue());
                if (rule.getSettleDay() != null) {
                    if (rule.getSettleDay() < 1 || rule.getSettleDay() > 28) {
                        throw new IllegalStateException("客户月结日无效");
                    }
                    due = due.plusDays(rule.getSettleDay() - 1L);
                }
            } else {
                throw new IllegalStateException("客户账期单位无效");
            }
        }
        receivable.setDueDate(due);
    }

    /**
     * 正常应付读取当前供应商账期，红字始终继承原应付快照。
     *
     * @param payable
     *            非空、已设置来源和业务时间的应付；红字原应付缺失时拒绝入账。
     */
    public void freezePayable(FinancePayableEntity payable) {
        if (ScmFinanceEntryTypeEnum.RED.name().equals(payable.getEntryType())) {
            FinancePayableEntity original = payableDao.selectById(payable.getOriginalPayableId());
            if (original == null) {
                throw new IllegalStateException("红字应付缺少原正常应付");
            }
            payable.setDueDate(original.getDueDate());
            payable.setPaymentRuleSnapshot(original.getPaymentRuleSnapshot());
            return;
        }
        Integer days = ruleDao.selectPaymentDays(payable.getSupplierId());
        days = days == null ? 0 : days;
        if (days < 0 || payable.getEventAt() == null) {
            throw new IllegalStateException("供应商账期或应付业务时间无效");
        }
        payable.setPaymentRuleSnapshot(Map.of("paymentPeriodDays", days));
        payable.setDueDate(payable.getEventAt().atZoneSameInstant(BUSINESS_ZONE).toLocalDate().plusDays(days));
    }
}
