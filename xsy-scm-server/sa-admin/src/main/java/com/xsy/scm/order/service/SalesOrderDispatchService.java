package com.xsy.scm.order.service;

import com.xsy.scm.order.dao.SalesOrderDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 发车出库把时间写回订单。
 *
 * <p>
 * 订单的「已发车」时间是 7 天自动锁单的起算点，只能由订单域写入；配送域负责线路与出库，需要回写时通过这里，不直接改订单表。
 */
@Service
@RequiredArgsConstructor
public class SalesOrderDispatchService {

    private final SalesOrderDao salesOrderDao;

    /**
     * 记录订单发车时间，返回是否写成功。
     *
     * <p>
     * 返回 {@code false} 表示订单不存在或已不是可发车状态，由调用方按自己的错误码拒绝，避免两个域的码值互相污染。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markDispatched(Long orderId, OffsetDateTime at, String operator) {
        return salesOrderDao.markDispatched(orderId, at, operator) == 1;
    }
}
