package com.xsy.scm.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.idempotency.ScmIdempotencyRequestHasher;
import com.xsy.scm.order.domain.form.OrderReturnDecisionForm;
import org.junit.jupiter.api.Test;

class OrderReturnMallCancelFingerprintTest {
    private final ScmIdempotencyRequestHasher hasher = new ScmIdempotencyRequestHasher(new ObjectMapper());

    @Test
    void retryAfterVersionChangeKeepsTheSameFingerprint() {
        String first = hasher.hash(OrderReturnService.mallCancelFingerprint(form(33L, 4, "客户自行撤销")));
        String retry = hasher.hash(OrderReturnService.mallCancelFingerprint(form(33L, 5, "客户自行撤销")));
        assertThat(retry).isEqualTo(first);
    }

    @Test
    void anotherReturnOrReasonCannotReplayTheFirstCancellation() {
        String original = hasher.hash(OrderReturnService.mallCancelFingerprint(form(33L, 4, "客户自行撤销")));
        assertThat(hasher.hash(OrderReturnService.mallCancelFingerprint(form(34L, 4, "客户自行撤销"))))
                .isNotEqualTo(original);
        assertThat(hasher.hash(OrderReturnService.mallCancelFingerprint(form(33L, 4, "不同原因"))))
                .isNotEqualTo(original);
    }

    private static OrderReturnDecisionForm form(Long returnId, int version, String reason) {
        OrderReturnDecisionForm form = new OrderReturnDecisionForm();
        form.setReturnId(returnId);
        form.setVersion(version);
        form.setDecisionReason(reason);
        return form;
    }
}
