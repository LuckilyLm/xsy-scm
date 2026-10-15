package com.xsy.scm.finance.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.dao.FinanceOperationLogDao;
import com.xsy.scm.finance.domain.entity.FinanceOperationLogEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 统一登记财务操作日志，必须与对应财务写入处于同一事务。 日志保存变更前后的金额事实，涉及余额时应在取得行锁后读取。
 */
@Component
@RequiredArgsConstructor
public class FinanceOperationLogRecorder {

    private final FinanceOperationLogDao financeOperationLogDao;
    private final ObjectMapper objectMapper;

    /**
     * 追加一条财务操作日志。
     *
     * <p>
     * {@code reason} 对破坏性动作必填（由调用方与对应事实表的 CHECK 共同保证）；{@code before} 可为 {@code null}（生成类动作没有「改前」）。{@code businessId}
     * 存的是事实主键而不是单号 —— 单号是读时 join 取得的派生展示值。
     */
    public void record(ScmFinanceBusinessTypeEnum businessType, Long businessId, ScmFinanceOperationTypeEnum operation,
            String reason, Object before, Object after) {
        record(businessType, businessId, operation, reason, before, after, ScmOperator.current());
    }

    /**
     * 追加一条财务操作日志，并<b>显式指定操作人</b>。
     *
     * <p>
     * 派生生成器用这一条：红字应收的操作人是 {@code xsy_order_return.updated_by}（批准人），正常应收的是
     * {@code xsy_delivery_route_order.signed_by}（签收人）。取已落库的业务事实操作人而不是
     * {@link ScmOperator#current()}，是为了让「财务事实的身份列」与「日志的操作人」来自同一个事实源 —— 否则同一笔账会出现两个可能对不上的操作人（「红字是已成立业务事实的映射」要求这一点成立）。
     */
    public void record(ScmFinanceBusinessTypeEnum businessType, Long businessId, ScmFinanceOperationTypeEnum operation,
            String reason, Object before, Object after, String operator) {
        var entry = new FinanceOperationLogEntity();
        entry.setBusinessType(businessType.name());
        entry.setBusinessId(businessId);
        entry.setOperationType(operation.name());
        entry.setOperator(operator);
        entry.setCreatedBy(operator);
        entry.setReason(reason);
        entry.setBeforeData(toJsonMap(before));
        entry.setAfterData(toJsonMap(after));
        financeOperationLogDao.insert(entry);
    }

    private Map<String, Object> toJsonMap(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
    }
}
