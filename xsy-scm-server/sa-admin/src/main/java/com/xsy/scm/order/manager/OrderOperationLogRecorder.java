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
 * 订单操作日志的唯一写入口。
 *
 * <p>
 * 原先是 {@code SalesOrderService} 的私有方法；退货与退款同样受 AGENTS.md 约束 （cancellation / refund / return 必须留操作日志），若各自再写一份就是三个事实源——
 * 操作人取值、JSON 列形态、before 可为空而 after 必填这些口径迟早会分叉。
 *
 * <p>
 * 调用方须在自身事务内使用：日志与业务变更必须同事务，否则会出现「库已改、日志没落」。
 */
@Component
@RequiredArgsConstructor
public class OrderOperationLogRecorder {
    private final OrderOperationLogDao orderOperationLogDao;
    private final ObjectMapper objectMapper;

    public void record(Long orderId, ScmOrderOperationTypeEnum operation, String reason, Object before, Object after) {
        var entry = new OrderOperationLogEntity();
        entry.setOrderId(orderId);
        entry.setOperationType(operation.name());
        entry.setOperator(ScmOperator.current());
        entry.setCreatedBy(entry.getOperator());
        entry.setReason(reason);
        entry.setBeforeData(before == null
                ? null
                : objectMapper.convertValue(before, new TypeReference<
                        Map<
                                String,
                                Object>>() {
                }));
        entry.setAfterData(objectMapper.convertValue(after, new TypeReference<
                Map<
                        String,
                        Object>>() {
        }));
        orderOperationLogDao.insert(entry);
    }
}
