package com.xsy.scm.order.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.dao.OrderOperationLogDao;
import com.xsy.scm.order.domain.entity.OrderOperationLogEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订单操作日志统一由本类写入，必须与订单、退货或退款变更处于同一事务。
 */
@Component
@RequiredArgsConstructor
public class OrderOperationLogRecorder {
    private final OrderOperationLogDao orderOperationLogDao;
    private final ObjectMapper objectMapper;

    public void record(Long orderId, ScmOrderOperationTypeEnum operation, String reason, Object before, Object after) {
        record(orderId, operation, reason, before, after, ScmOperator.current());
    }

    /**
     * 定时任务等没有登录上下文时用显式操作人写入，例如发车满 7 天的自动锁单。
     */
    public void record(Long orderId, ScmOrderOperationTypeEnum operation, String reason, Object before, Object after,
        String operator) {
        record(orderId, null, operation, reason, before, after, operator);
    }

    /**
     * 独立退货没有订单，日志主体记客户；订单日志与客户日志共用同一张表和同一套 jsonb 快照。
     */
    public void recordForCustomer(Long customerId, ScmOrderOperationTypeEnum operation, String reason, Object before,
        Object after) {
        record(null, customerId, operation, reason, before, after, ScmOperator.current());
    }

    private void record(Long orderId, Long customerId, ScmOrderOperationTypeEnum operation, String reason,
        Object before, Object after, String operator) {
        var entry = new OrderOperationLogEntity();
        entry.setOrderId(orderId);
        entry.setCustomerId(customerId);
        entry.setOperationType(operation.name());
        entry.setOperator(operator);
        entry.setCreatedBy(entry.getOperator());
        entry.setReason(reason);
        entry.setBeforeData(
            before == null ? null : objectMapper.convertValue(before, new TypeReference<Map<String, Object>>() {
            }));
        entry.setAfterData(objectMapper.convertValue(after, new TypeReference<Map<String, Object>>() {
        }));
        orderOperationLogDao.insert(entry);
    }
}
