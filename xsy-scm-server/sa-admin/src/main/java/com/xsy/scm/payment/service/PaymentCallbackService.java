package com.xsy.scm.payment.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentCallbackEventTypeEnum;
import com.xsy.scm.payment.constant.ScmPaymentCallbackProcessStatusEnum;
import com.xsy.scm.payment.dao.PaymentCallbackEventDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.entity.PaymentCallbackEventEntity;
import com.xsy.scm.payment.domain.entity.PaymentRefundEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付回调按验签、保存事件、核对交易、更新状态、登记收款的顺序处理。同一渠道事件不能重复入账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

    private final PaymentCallbackEventDao paymentCallbackEventDao;

    private final PaymentTransactionDao paymentTransactionDao;

    private final PaymentIntentService paymentIntentService;

    private final PaymentRefundService paymentRefundService;

    private final ScmPaymentProviderRegistry providerRegistry;

    /**
     * 处理一次渠道回调。
     *
     * @return 本次处理结论（重复事件也返回结论，调用方据此决定回什么给渠道）
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentCallbackEventEntity handle(String providerCode, Map<String, String> headers, String rawBody) {
        ScmPaymentProvider provider = providerRegistry.require(providerCode);
        ScmPaymentProvider.Callback callback = provider.parseCallback(headers, rawBody);

        // 1) 幂等锚点必须存在：没有渠道事件 id 就无从判定重复，宁可拒收也不冒「重复入账」的险
        if (callback.providerEventId() == null || callback.providerEventId().isBlank()) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_CALLBACK_PAYLOAD_INVALID);
        }

        String operator = ScmOperator.current();
        PaymentCallbackEventEntity event = new PaymentCallbackEventEntity();
        event.setProvider(providerCode);
        event.setProviderEventId(callback.providerEventId());
        // 归一化类型优先；不认识时<b>原样存渠道给的名字</b>（payload 里有什么就存什么），
        // 两个都没有才留空 —— 留证比编一个本地占位值诚实，也避免造出与其它域撞值的魔法字符串。
        event.setEventType(callback.eventType() != null ? callback.eventType().name() : callback.rawEventType());
        event.setSignatureVerified(callback.signatureVerified());
        event.setPayloadHash(hash(rawBody));
        event.setPayload(callback.payload());
        event.setProcessStatus(ScmPaymentCallbackProcessStatusEnum.RECEIVED.name());
        event.setCreatedBy(operator);

        // 2) 幂等：已存在则静默返回「重复事件」，<b>不做任何状态转换</b>
        if (paymentCallbackEventDao.insertIgnoreDuplicate(event) != 1) {
            log.info("支付回调重复投递，已忽略：provider={} eventId={}", providerCode, callback.providerEventId());
            return paymentCallbackEventDao.selectClaimedByProviderEventId(providerCode, callback.providerEventId());
        }

        // 3) 验签：不通过只留证，绝不进入状态转换
        if (!callback.signatureVerified()) {
            return reject(event.getId(), "验签失败：" + callback.rejectReason());
        }
        if (callback.eventType() == null) {
            return reject(event.getId(), "事件类型不认识：" + callback.rejectReason());
        }

        // 4) 匹配交易：渠道交易号是回调与本地交易的唯一连接点
        PaymentTransactionEntity transaction = paymentTransactionDao.selectByProviderTransactionNo(providerCode,
            callback.providerTransactionNo());
        if (transaction == null) {
            return reject(event.getId(), "找不到对应的支付交易：" + callback.providerTransactionNo());
        }

        // 5) 状态机：支付与退款都走<b>这一个</b> dispatcher，各自复用与发起时同一段判定，
        // 不各写一份，也不留第二条处理路径
        Long linkedTransactionId = transaction.getId();
        switch (callback.eventType()) {
            case PAYMENT_SUCCEEDED -> paymentIntentService.applyOutcome(transaction.getIntentId(), transaction.getId(),
                ScmPaymentProvider.Outcome.SUCCEEDED, null, null, callback.amount(), operator);
            case PAYMENT_FAILED -> paymentIntentService.applyOutcome(transaction.getIntentId(), transaction.getId(),
                ScmPaymentProvider.Outcome.FAILED, "PROVIDER_FAILED", "渠道回调支付失败", null, operator);
            case REFUND_SUCCEEDED, REFUND_FAILED -> {
                // 退款回调按<b>渠道退款号</b>匹配退款事实；缺它就无从定位，宁可拒收也不猜
                if (callback.providerRefundNo() == null || callback.providerRefundNo().isBlank()) {
                    return reject(event.getId(), "退款回调缺少渠道退款号");
                }
                PaymentRefundEntity refund = paymentRefundService.findByProviderRefundNo(providerCode,
                    callback.providerRefundNo());
                if (refund == null) {
                    return reject(event.getId(), "找不到对应的支付退款：" + callback.providerRefundNo());
                }
                linkedTransactionId = refund.getTransactionId();
                paymentRefundService.applyOutcome(refund.getId(),
                    callback.eventType() == ScmPaymentCallbackEventTypeEnum.REFUND_SUCCEEDED
                        ? ScmPaymentProvider.Outcome.SUCCEEDED
                        : ScmPaymentProvider.Outcome.FAILED,
                    callback.providerRefundNo(), callback.amount(), "PROVIDER_REFUND_FAILED", "渠道回调退款失败", operator);
            }
        }

        paymentCallbackEventDao.markProcessed(event.getId(), ScmPaymentCallbackProcessStatusEnum.APPLIED.name(),
            linkedTransactionId, null);
        return paymentCallbackEventDao.selectById(event.getId());
    }

    private PaymentCallbackEventEntity reject(Long eventId, String reason) {
        paymentCallbackEventDao.markProcessed(eventId, ScmPaymentCallbackProcessStatusEnum.REJECTED.name(), null,
            reason);
        log.warn("支付回调被拒绝：eventId={} reason={}", eventId, reason);
        return paymentCallbackEventDao.selectById(eventId);
    }

    private static String hash(String rawBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
