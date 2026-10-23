package com.xsy.scm.purchase.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.constant.ScmPurchaseInquiryStatusEnum;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO.Inquiry;

import java.time.OffsetDateTime;
import java.util.Objects;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_INQUIRY_STATE_INVALID;

/**
 * 报价接收截止与采购员选用期限是两个不同的业务边界。
 */
public final class PurchaseInquiryDecisionPolicy {
    private PurchaseInquiryDecisionPolicy() {
    }

    public static void requireQuoteWritable(Inquiry inquiry, Integer expectedVersion, OffsetDateTime now) {
        requireAwaitingDecision(inquiry, expectedVersion);
        if (!inquiry.getDeadlineAt().isAfter(now)) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_STATE_INVALID);
        }
    }

    public static void requireAwaitingDecision(Inquiry inquiry, Integer expectedVersion) {
        if (!Objects.equals(inquiry.getVersion(), expectedVersion)) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        if (!ScmPurchaseInquiryStatusEnum.OPEN.name().equals(inquiry.getStatus())) {
            throw new ScmBusinessException(PURCHASE_INQUIRY_STATE_INVALID);
        }
    }

    public static boolean isQuoteStillValid(OffsetDateTime validUntil, OffsetDateTime now) {
        return validUntil != null && validUntil.isAfter(now);
    }
}
