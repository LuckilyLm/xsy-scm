package com.xsy.scm.purchase.job;

import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.service.PurchasePriceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 采购价窗口到期自动回填：待定价超过 7 天的行，按最近一次已提交采购价补上。
 *
 * <p>
 * 逐单独立事务：一行失败不拖住整批。没有参考价的行保持待定价并记警告（{@link PurchasePriceService#autoFill}）， 随后自动锁单会把它锁住，避免继续往一张老单里补价。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scm.purchase.price-auto-fill.enabled", havingValue = "true", matchIfMissing = true)
public class PurchasePriceAutoFillJob {

    private static final String DELAY = "${scm.purchase.price-auto-fill.delay-ms:3600000}";

    private static final String INITIAL_DELAY = "${scm.purchase.price-auto-fill.initial-delay-ms:90000}";

    private static final int BATCH_SIZE = 100;

    private final PurchaseOrderItemDao purchaseOrderItemDao;

    private final PurchasePriceService purchasePriceService;

    @Scheduled(fixedDelayString = DELAY, initialDelayString = INITIAL_DELAY)
    public void autoFillExpiredPrices() {
        for (Long orderId : purchaseOrderItemDao.pendingPriceOrderIds(BATCH_SIZE)) {
            try {
                if (purchasePriceService.autoFill(orderId)) {
                    log.info("采购价窗口到期，已按最近已提交价回填：orderId={}", orderId);
                }
            } catch (RuntimeException exception) {
                log.warn("采购价自动回填失败：orderId={}", orderId, exception);
            }
        }
    }
}
