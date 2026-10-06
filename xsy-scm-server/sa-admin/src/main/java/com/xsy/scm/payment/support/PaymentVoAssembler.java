package com.xsy.scm.payment.support;

import com.xsy.scm.payment.domain.entity.PaymentCallbackEventEntity;
import com.xsy.scm.payment.domain.entity.PaymentIntentEntity;
import com.xsy.scm.payment.domain.entity.PaymentReconciliationEntity;
import com.xsy.scm.payment.domain.entity.PaymentReconciliationItemEntity;
import com.xsy.scm.payment.domain.entity.PaymentRefundEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.domain.vo.PaymentCallbackEventVO;
import com.xsy.scm.payment.domain.vo.PaymentIntentVO;
import com.xsy.scm.payment.domain.vo.PaymentReconciliationItemVO;
import com.xsy.scm.payment.domain.vo.PaymentReconciliationVO;
import com.xsy.scm.payment.domain.vo.PaymentRefundVO;
import com.xsy.scm.payment.domain.vo.PaymentTransactionVO;

/**
 * 实体 → VO 的转换（纯函数）。
 *
 * <p>
 * 单独放一处而不是让 Controller 各拼各的：VO 是<b>对外契约</b>，拼装散在多个 Controller 里， 迟早出现「列表接口有金额、详情接口漏了金额」这种不一致。
 */
public final class PaymentVoAssembler {

    private PaymentVoAssembler() {
    }

    public static PaymentIntentVO toIntent(PaymentIntentEntity row) {
        if (row == null) {
            return null;
        }
        PaymentIntentVO vo = new PaymentIntentVO();
        vo.setId(row.getId());
        vo.setIntentNo(row.getIntentNo());
        vo.setCustomerId(row.getCustomerId());
        vo.setCustomerNameSnapshot(row.getCustomerNameSnapshot());
        vo.setSourceType(row.getSourceType());
        vo.setSourceId(row.getSourceId());
        vo.setSourceNoSnapshot(row.getSourceNoSnapshot());
        vo.setAmount(row.getAmount());
        vo.setMethod(row.getMethod());
        vo.setProvider(row.getProvider());
        vo.setStatus(row.getStatus());
        vo.setExternalIntentId(row.getExternalIntentId());
        vo.setMockScenario(row.getMockScenario());
        vo.setExpireAt(row.getExpireAt());
        vo.setSucceededAt(row.getSucceededAt());
        vo.setClosedAt(row.getClosedAt());
        vo.setRemark(row.getRemark());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setCreatedBy(row.getCreatedBy());
        return vo;
    }

    public static PaymentTransactionVO toTransaction(PaymentTransactionEntity row) {
        if (row == null) {
            return null;
        }
        PaymentTransactionVO vo = new PaymentTransactionVO();
        vo.setId(row.getId());
        vo.setTransactionNo(row.getTransactionNo());
        vo.setIntentId(row.getIntentId());
        vo.setProvider(row.getProvider());
        vo.setProviderTransactionNo(row.getProviderTransactionNo());
        vo.setAmount(row.getAmount());
        vo.setProviderAmount(row.getProviderAmount());
        vo.setStatus(row.getStatus());
        vo.setPaidAt(row.getPaidAt());
        vo.setFailureCode(row.getFailureCode());
        vo.setFailureMessage(row.getFailureMessage());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setCreatedBy(row.getCreatedBy());
        return vo;
    }

    public static PaymentRefundVO toRefund(PaymentRefundEntity row) {
        if (row == null) {
            return null;
        }
        PaymentRefundVO vo = new PaymentRefundVO();
        vo.setId(row.getId());
        vo.setRefundNo(row.getRefundNo());
        vo.setIntentId(row.getIntentId());
        vo.setTransactionId(row.getTransactionId());
        vo.setProvider(row.getProvider());
        vo.setAmount(row.getAmount());
        vo.setSourceType(row.getSourceType());
        vo.setSourceId(row.getSourceId());
        vo.setStatus(row.getStatus());
        vo.setProviderRefundNo(row.getProviderRefundNo());
        vo.setMockScenario(row.getMockScenario());
        vo.setReason(row.getReason());
        vo.setRefundedAt(row.getRefundedAt());
        vo.setFailureCode(row.getFailureCode());
        vo.setFailureMessage(row.getFailureMessage());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setCreatedBy(row.getCreatedBy());
        return vo;
    }

    public static PaymentCallbackEventVO toCallbackEvent(PaymentCallbackEventEntity row) {
        if (row == null) {
            return null;
        }
        PaymentCallbackEventVO vo = new PaymentCallbackEventVO();
        vo.setId(row.getId());
        vo.setProvider(row.getProvider());
        vo.setProviderEventId(row.getProviderEventId());
        vo.setEventType(row.getEventType());
        vo.setSignatureVerified(row.getSignatureVerified());
        vo.setPayloadHash(row.getPayloadHash());
        vo.setPayload(row.getPayload());
        vo.setTransactionId(row.getTransactionId());
        vo.setProcessStatus(row.getProcessStatus());
        vo.setRejectReason(row.getRejectReason());
        vo.setReceivedAt(row.getReceivedAt());
        vo.setProcessedAt(row.getProcessedAt());
        return vo;
    }

    public static PaymentReconciliationVO toReconciliation(PaymentReconciliationEntity row) {
        if (row == null) {
            return null;
        }
        PaymentReconciliationVO vo = new PaymentReconciliationVO();
        vo.setId(row.getId());
        vo.setReconciliationNo(row.getReconciliationNo());
        vo.setProvider(row.getProvider());
        vo.setBizDate(row.getBizDate());
        vo.setStatus(row.getStatus());
        vo.setProviderTotal(row.getProviderTotal());
        vo.setLocalTotal(row.getLocalTotal());
        vo.setDifference(row.getDifference());
        vo.setProviderCount(row.getProviderCount());
        vo.setLocalCount(row.getLocalCount());
        vo.setTotalCount(row.getTotalCount());
        vo.setMatchedCount(row.getMatchedCount());
        vo.setDifferenceCount(row.getDifferenceCount());
        vo.setDetail(row.getDetail());
        vo.setReconciledAt(row.getReconciledAt());
        vo.setRemark(row.getRemark());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setCreatedBy(row.getCreatedBy());
        return vo;
    }

    public static PaymentReconciliationItemVO toReconciliationItem(PaymentReconciliationItemEntity row) {
        if (row == null) {
            return null;
        }
        PaymentReconciliationItemVO vo = new PaymentReconciliationItemVO();
        vo.setId(row.getId());
        vo.setReconciliationId(row.getReconciliationId());
        vo.setCategory(row.getCategory());
        vo.setProviderTransactionNo(row.getProviderTransactionNo());
        vo.setTransactionId(row.getTransactionId());
        vo.setLocalAmount(row.getLocalAmount());
        vo.setProviderAmount(row.getProviderAmount());
        vo.setLocalStatus(row.getLocalStatus());
        vo.setProviderStatus(row.getProviderStatus());
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }
}
