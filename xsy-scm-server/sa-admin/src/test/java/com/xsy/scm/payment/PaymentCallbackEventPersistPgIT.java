package com.xsy.scm.payment;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.payment.dao.PaymentCallbackEventDao;
import com.xsy.scm.payment.domain.entity.PaymentCallbackEventEntity;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回调事件落库与结论回填（FIX-09，PG IT）。
 *
 * <p><b>缺陷</b>：{@code insertIgnoreDuplicate} 的 mapper 漏了 {@code useGeneratedKeys}，
 * 插入后 {@code entity.getId()} 恒为 null；{@code PaymentCallbackService} 靠这个 id 调
 * {@code markProcessed}，于是执行的是 {@code WHERE id = NULL}，更新 0 行 —— 回调事件永远停在
 * {@code RECEIVED}，{@code transaction_id} 与 {@code reject_reason} 也永远为空。
 * 这与「伪造回调要如实记成 REJECTED 留证」的设计意图直接冲突。
 *
 * <p>本类钉住两件事：① 插入必须回填主键；② 有了主键之后，APPLIED / REJECTED 两条分支都能落地。
 * {@code ON CONFLICT DO NOTHING} 分支由 Service 用受影响行数提前 return，不依赖 id，此处一并钉住。
 */
@DisplayName("回调事件落库与结论回填（FIX-09，PG IT）")
class PaymentCallbackEventPersistPgIT extends ScmW5PgITBase {

    @Autowired
    private PaymentCallbackEventDao paymentCallbackEventDao;

    @Test
    @DisplayName("插入必须回填主键：漏了它 markProcessed 会更新 0 行")
    void insertBackfillsGeneratedId() {
        PaymentCallbackEventEntity event = newEvent("APPLIED-CASE");
        assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(event)).isEqualTo(1);
        assertThat(event.getId())
                .as("插入后必须回填自增主键，否则 markProcessed 只能执行 WHERE id = NULL")
                .isNotNull();
        assertThat(event.getId()).isPositive();
    }

    @Test
    @DisplayName("APPLIED：验签通过并匹配到交易后，状态与交易关联都要落地")
    void appliedStatusIsPersisted() {
        PaymentCallbackEventEntity event = newEvent("APPLIED-CASE");
        assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(event)).isEqualTo(1);

        // 真实交易存在与否不影响本次断言：这里只验证「回填结论」这一步能落到正确的行上
        Long linkedTransactionId = 987654321L;
        assertThat(paymentCallbackEventDao.markProcessed(event.getId(), "APPLIED", linkedTransactionId, null))
                .as("必须正好更新到刚插入的那一行")
                .isEqualTo(1);

        PaymentCallbackEventEntity reloaded = paymentCallbackEventDao.selectByProviderEventId("MOCK",
                event.getProviderEventId());
        assertThat(reloaded.getProcessStatus()).isEqualTo("APPLIED");
        assertThat(reloaded.getTransactionId()).isEqualTo(linkedTransactionId);
        assertThat(reloaded.getRejectReason()).isNull();
        assertThat(reloaded.getProcessedAt()).as("回填结论必须同时打上处理时间").isNotNull();
    }

    @Test
    @DisplayName("REJECTED：验签失败要留下拒因，不能停在 RECEIVED")
    void rejectedStatusAndReasonArePersisted() {
        PaymentCallbackEventEntity event = newEvent("REJECTED-CASE");
        event.setSignatureVerified(false);
        assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(event)).isEqualTo(1);

        assertThat(paymentCallbackEventDao.markProcessed(event.getId(), "REJECTED", null, "验签失败：签名不匹配"))
                .isEqualTo(1);

        PaymentCallbackEventEntity reloaded = paymentCallbackEventDao.selectByProviderEventId("MOCK",
                event.getProviderEventId());
        assertThat(reloaded.getProcessStatus()).isEqualTo("REJECTED");
        assertThat(reloaded.getRejectReason()).isEqualTo("验签失败：签名不匹配");
        assertThat(reloaded.getSignatureVerified()).as("未通过验签的事实本身也要留证").isFalse();
        assertThat(reloaded.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("重复事件：ON CONFLICT 不报错且返回 0，Service 据此提前返回、不依赖 id")
    void duplicateEventReturnsZeroWithoutTouchingTheRow() {
        PaymentCallbackEventEntity first = newEvent("DUP-CASE");
        assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(first)).isEqualTo(1);

        // 幂等锚点是 (provider, provider_event_id)：第二个实体必须复用同一个事件 id，才谈得上「重复投递」
        PaymentCallbackEventEntity duplicate = newEvent("DUP-CASE");
        duplicate.setProviderEventId(first.getProviderEventId());
        assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(duplicate))
                .as("同一 (provider, provider_event_id) 重复投递必须被唯一索引挡下并返回 0")
                .isZero();

        PaymentCallbackEventEntity reloaded = paymentCallbackEventDao.selectByProviderEventId("MOCK",
                first.getProviderEventId());
        assertThat(reloaded.getProcessStatus()).as("重复投递不得改动已存在的事件").isEqualTo("RECEIVED");
        assertThat(reloaded.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("拒绝事件保留证据，同 ID 合法事件仍可处理且不能重复应用")
    void rejectedEventDoesNotClaimTheValidEventId() {
        PaymentCallbackEventEntity rejected = newEvent("RETRY-AFTER-REJECT");
        rejected.setSignatureVerified(false);
        assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(rejected)).isEqualTo(1);
        assertThat(paymentCallbackEventDao.markProcessed(rejected.getId(), "REJECTED", null, "验签失败"))
                .isEqualTo(1);

        PaymentCallbackEventEntity valid = newEvent("RETRY-VALID");
        valid.setProviderEventId(rejected.getProviderEventId());
        assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(valid)).isEqualTo(1);
        assertThat(paymentCallbackEventDao.markProcessed(valid.getId(), "APPLIED", null, null)).isEqualTo(1);

        PaymentCallbackEventEntity duplicate = newEvent("RETRY-DUPLICATE");
        duplicate.setProviderEventId(rejected.getProviderEventId());
        assertThat(paymentCallbackEventDao.insertIgnoreDuplicate(duplicate)).isZero();
        assertThat(paymentCallbackEventDao.selectById(rejected.getId()).getProcessStatus()).isEqualTo("REJECTED");
        assertThat(paymentCallbackEventDao.selectClaimedByProviderEventId("MOCK", rejected.getProviderEventId())
                .getId()).isEqualTo(valid.getId());
    }

    private PaymentCallbackEventEntity newEvent(String marker) {
        PaymentCallbackEventEntity event = new PaymentCallbackEventEntity();
        event.setProvider("MOCK");
        event.setProviderEventId("IT-FIX09-" + marker + "-" + UUID.randomUUID());
        event.setEventType("PAYMENT_SUCCEEDED");
        event.setSignatureVerified(true);
        event.setPayloadHash(UUID.randomUUID().toString().replace("-", ""));
        event.setPayload(Map.of("eventId", event.getProviderEventId()));
        event.setProcessStatus("RECEIVED");
        event.setCreatedBy("IT-FIX09");
        return event;
    }
}
