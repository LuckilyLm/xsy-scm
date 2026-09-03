package com.xianshuyuan.scm.order.service;

import com.xianshuyuan.scm.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStateTransitionPolicyTest {

    @Test
    void allowsDraftSubmission() {
        assertThat(OrderStateTransitionPolicy.canTransition(OrderStatus.DRAFT, OrderStatus.PENDING))
            .isTrue();
    }

    @Test
    void allowsPendingConfirmation() {
        assertThat(OrderStateTransitionPolicy.canTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED))
            .isTrue();
    }

    @Test
    void allowsCancellationFromDraftAndPending() {
        assertThat(OrderStateTransitionPolicy.canTransition(OrderStatus.DRAFT, OrderStatus.CANCELLED))
            .isTrue();
        assertThat(OrderStateTransitionPolicy.canTransition(OrderStatus.PENDING, OrderStatus.CANCELLED))
            .isTrue();
    }

    @Test
    void rejectsSkippedAndBackwardTransitions() {
        assertThat(OrderStateTransitionPolicy.canTransition(OrderStatus.DRAFT, OrderStatus.CONFIRMED))
            .isFalse();
        assertThat(OrderStateTransitionPolicy.canTransition(OrderStatus.PENDING, OrderStatus.DRAFT))
            .isFalse();
    }

    @Test
    void rejectsTransitionsFromTerminalStates() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStateTransitionPolicy.canTransition(OrderStatus.CONFIRMED, target))
                .isFalse();
            assertThat(OrderStateTransitionPolicy.canTransition(OrderStatus.CANCELLED, target))
                .isFalse();
        }
    }

    @Test
    void rejectsTransitionToSameState() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(OrderStateTransitionPolicy.canTransition(status, status)).isFalse();
        }
    }
}
