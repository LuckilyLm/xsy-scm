package net.lab1024.sa.admin.module.scm.order.manager;

import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;

import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Order lifecycle only. Fulfillment never enters this state graph.
 */
public final class OrderStateMachine {
    private OrderStateMachine() {
    }

    public static boolean canTransition(String from, String to) {
        return switch (from) {
            case "DRAFT" -> Set.of("PENDING", "CANCELLED").contains(to);
            case "PENDING" -> Set.of("CONFIRMED", "CANCELLED").contains(to);
            default -> false;
        };
    }

    public static void transition(String from, String to) {
        if (!canTransition(from, to)) throw new ScmBusinessException(ORDER_STATE_INVALID);
    }

    public static void editable(String state) {
        if (!"DRAFT".equals(state)) throw new ScmBusinessException(ORDER_STATE_INVALID);
    }

    public static void actualQuantity(String state) {
        if (!"PENDING".equals(state)) throw new ScmBusinessException(ORDER_STATE_INVALID);
    }
}
