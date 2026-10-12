package com.xsy.scm.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.idempotency.ScmIdempotencyRequestHasher;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import org.junit.jupiter.api.Test;

/**
 * 商城取消的幂等指纹必须对「合法重试」稳定。
 *
 * <p>
 * 第一次取消成功后订单版本会前进，重试读到的是新版本。若把 version 算进指纹，
 * 重试会被判成「内容不同的请求」而报幂等冲突，幂等就形同虚设。
 */
class SalesOrderCancelFingerprintTest {
    private final ScmIdempotencyRequestHasher hasher = new ScmIdempotencyRequestHasher(new ObjectMapper());

    @Test
    void mallCancelFingerprintIgnoresTheOrderVersionSoRetriesStillReplay() {
        OrderCancelForm firstAttempt = form(21L, 4, "下错单了");
        OrderCancelForm retry = form(21L, 5, "下错单了");

        // 原始表单把 version 算进哈希，版本前进后重试就对不上了
        assertThat(hasher.hash(firstAttempt)).isNotEqualTo(hasher.hash(retry));
        // 商城指纹只认订单与原因，重试仍命中同一条幂等记录
        assertThat(hasher.hash(SalesOrderService.cancelFingerprint(firstAttempt)))
                .isEqualTo(hasher.hash(SalesOrderService.cancelFingerprint(retry)));
    }

    @Test
    void mallCancelFingerprintStillSeparatesDifferentOrdersAndReasons() {
        String baseline = hasher.hash(SalesOrderService.cancelFingerprint(form(21L, 4, "下错单了")));

        assertThat(hasher.hash(SalesOrderService.cancelFingerprint(form(22L, 4, "下错单了"))))
                .isNotEqualTo(baseline);
        assertThat(hasher.hash(SalesOrderService.cancelFingerprint(form(21L, 4, "不需要了"))))
                .isNotEqualTo(baseline);
    }

    private static OrderCancelForm form(Long orderId, int version, String reason) {
        OrderCancelForm form = new OrderCancelForm();
        form.setOrderId(orderId);
        form.setVersion(version);
        form.setReason(reason);
        return form;
    }
}
