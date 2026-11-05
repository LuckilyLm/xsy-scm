package com.xsy.scm.purchase.job;

import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.service.PurchaseOrderLockService;
import com.xsy.scm.purchase.service.PurchasePriceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 提交满 7 天的采购单自动锁单。
 *
 * <p>
 * 起算点是 {@code auto_lock_at = submitted_at + 7 天}，与价格窗口同一锚点；锁单只关闭补价/改价， 收货与应付照常进行。每轮只取一批候选，避免一次扫描拖住数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scm.purchase.auto-lock.enabled", havingValue = "true", matchIfMissing = true)
public class PurchaseOrderAutoLockJob {

    private static final String DELAY = "${scm.purchase.auto-lock.delay-ms:3600000}";

    private static final String INITIAL_DELAY = "${scm.purchase.auto-lock.initial-delay-ms:60000}";

    private static final int BATCH_SIZE = 200;

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchasePriceService purchasePriceService;

    private final PurchaseOrderLockService purchaseOrderLockService;

    /**
     * 补价窗口与自动锁单是同一个到期日，顺序不能交给两个任务的初始延迟去碰运气： 一旦先锁单，补价任务就会因为「已锁单」永远跳过这张单。这里固定先补价、再锁单。
     */
    @Scheduled(fixedDelayString = DELAY, initialDelayString = INITIAL_DELAY)
    public void lockExpiredOrders() {
        for (Long orderId : purchaseOrderDao.autoLockCandidates(BATCH_SIZE)) {
            try {
                purchasePriceService.autoFill(orderId);
            } catch (RuntimeException exception) {
                // 补价失败不阻断锁单：没有参考价的行本来就该带着待定价被锁住，等人工处理
                log.warn("采购单自动补价失败：orderId={}", orderId, exception);
            }
            try {
                if (purchaseOrderLockService.autoLock(orderId)) {
                    log.info("采购单提交满 {} 天自动锁单：orderId={}", PurchaseOrderLockService.PRICE_WINDOW_DAYS, orderId);
                }
            } catch (RuntimeException exception) {
                log.warn("采购单自动锁单失败：orderId={}", orderId, exception);
            }
        }
    }
}
