package com.xianshuyuan.scm.order.service;

import com.xianshuyuan.scm.order.entity.OrderStatus;

import java.util.Set;

public final class OrderStateTransitionPolicy {

    private static final Set<Transition> ALLOWED_TRANSITIONS = Set.of(
        new Transition(OrderStatus.DRAFT, OrderStatus.PENDING),
        new Transition(OrderStatus.DRAFT, OrderStatus.CANCELLED),
        new Transition(OrderStatus.PENDING, OrderStatus.CONFIRMED),
        new Transition(OrderStatus.PENDING, OrderStatus.CANCELLED)
    );

    private OrderStateTransitionPolicy() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED_TRANSITIONS.contains(new Transition(from, to));
    }

    private record Transition(OrderStatus from, OrderStatus to) {
    }
}
