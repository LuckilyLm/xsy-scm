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
 * 财务操作日志的唯一写入口。
 *
 * <p>
 * <b>调用方必须在自身事务内使用</b>：日志与财务事实变更必须同事务， 否则会出现「账已改、日志没落」—— 对财务而言那等于证据链断裂。 本类不加 {@code @Transactional}，事务边界由调用方（写命令）决定。
 *
 * <p>
 * <b>不复用 {@code t_operate_log}</b>：通用日志不保证与业务事务同成同败， 也不带金额快照与类型白名单。财务需要的是「改前 / 改后金额级证据」， 因此 {@code before} /
 * {@code after} 两个 JSONB 快照是本表存在的理由。
 *
 * <p>
 * 快照口径：
 * <ul>
 * <li>生成类（{@code GENERATE} / {@code RED_GENERATE}）：{@code before} 为 {@code null}， {@code after} 为单头快照；</li>
 * <li>核销与反向核销：{@code before} 为目标的派生余额快照，{@code after} 为写入后的派生余额快照；</li>
 * <li>收付款反向：{@code before} 为原行有效额快照，{@code after} 为反向后快照。</li>
 * </ul>
 * 派生余额快照必须在持有目标行锁之后取，否则记下的是一份并发下已经不成立的数字。
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
     * 追加一条财务操作日志，并**显式指定操作人**。
     *
     * <p>
     * 派生生成器用这一条：红字应收的操作人是 {@code order_return.updated_by}（批准人）， 正常应收的是
     * {@code delivery_route_order.signed_by}（签收人）。取已落库的业务事实操作人 而不是
     * {@link ScmOperator#current()}，是为了让「财务事实的身份列」与「日志的操作人」 来自同一个事实源 —— 否则同一笔账会出现两个可能对不上的操作人（「红字是已成立业务事实的映射」要求这一点成立）。
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
