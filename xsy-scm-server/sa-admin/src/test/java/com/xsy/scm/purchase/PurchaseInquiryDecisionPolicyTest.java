package com.xsy.scm.purchase;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO.Inquiry;
import com.xsy.scm.purchase.support.PurchaseInquiryDecisionPolicy;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseInquiryDecisionPolicyTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-17T17:30:00+08:00");

    @Test
    void acceptingExistingQuoteIsAllowedAfterReplyDeadline() {
        Inquiry inquiry = inquiry(3, "OPEN", NOW.minusMinutes(30));
        assertThatCode(() -> PurchaseInquiryDecisionPolicy.requireAwaitingDecision(inquiry, 3))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PurchaseInquiryDecisionPolicy.requireQuoteWritable(inquiry, 3, NOW))
                .isInstanceOf(ScmBusinessException.class);
        assertThat(PurchaseInquiryDecisionPolicy.isQuoteStillValid(NOW.plusDays(1), NOW)).isTrue();
    }

    @Test
    void staleVersionAndAcceptedInquiryRejectFurtherDecisions() {
        Inquiry inquiry = inquiry(4, "OPEN", NOW.plusMinutes(10));
        assertThatThrownBy(() -> PurchaseInquiryDecisionPolicy.requireAwaitingDecision(inquiry, 3))
                .isInstanceOf(ScmBusinessException.class);
        inquiry.setStatus("ACCEPTED");
        assertThatThrownBy(() -> PurchaseInquiryDecisionPolicy.requireAwaitingDecision(inquiry, 4))
                .isInstanceOf(ScmBusinessException.class);
    }

    @Test
    void expiredQuoteCannotBeAccepted() {
        assertThat(PurchaseInquiryDecisionPolicy.isQuoteStillValid(NOW, NOW)).isFalse();
        assertThat(PurchaseInquiryDecisionPolicy.isQuoteStillValid(NOW.minusSeconds(1), NOW)).isFalse();
        assertThat(PurchaseInquiryDecisionPolicy.isQuoteStillValid(null, NOW)).isFalse();
    }

    private static Inquiry inquiry(int version, String status, OffsetDateTime deadline) {
        Inquiry row = new Inquiry();
        row.setVersion(version);
        row.setStatus(status);
        row.setDeadlineAt(deadline);
        return row;
    }
}
