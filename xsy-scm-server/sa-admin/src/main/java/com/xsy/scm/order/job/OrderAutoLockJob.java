package com.xsy.scm.order.job;

import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.service.OrderLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 发车出库满 7 天的订单自动锁单。
 *
 * <p>
 * 起算点是 {@code dispatched_at}（发车出库那一刻），不是配送单的履约状态：履约状态会被签收、异常等后续操作改写， 反推不出「发车多久了」。每轮只取一批候选，避免一次扫描拖住数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scm.order.auto-lock.enabled", havingValue = "true", matchIfMissing = true)
public class OrderAutoLockJob {
    private static final String DELAY = "${scm.order.auto-lock.delay-ms:3600000}";
    private static final String INITIAL_DELAY = "${scm.order.auto-lock.initial-delay-ms:60000}";
    private static final int BATCH_SIZE = 200;

    private final SalesOrderDao salesOrderDao;
    private final OrderLockService orderLockService;

    @Scheduled(fixedDelayString = DELAY, initialDelayString = INITIAL_DELAY)
    public void lockDispatchedOrders() {
        for (Long orderId : salesOrderDao.autoLockCandidates(BATCH_SIZE)) {
            try {
                if (orderLockService.autoLock(orderId))
                    log.info("发车满 {} 天自动锁单：orderId={}", OrderLockService.AUTO_LOCK_DAYS, orderId);
            } catch (RuntimeException exception) {
                log.warn("自动锁单失败：orderId={}", orderId, exception);
            }
        }
    }
}
