package com.xsy.scm.order.manager;



import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_STATE_INVALID;

import java.math.BigDecimal;

/**
 * Order lifecycle only. Fulfillment never enters this state graph.
 */
public final class OrderStateMachine {
    private OrderStateMachine() {
    }

    public static boolean canTransition(String from, String to) {
        ScmOrderStatusEnum fromStatus;
        try {
            fromStatus = ScmOrderStatusEnum.valueOf(from);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return switch (fromStatus) {
            case DRAFT -> to.equals(ScmOrderStatusEnum.PENDING.name())
                || to.equals(ScmOrderStatusEnum.CANCELLED.name());
            case PENDING -> to.equals(ScmOrderStatusEnum.CONFIRMED.name())
                || to.equals(ScmOrderStatusEnum.CANCELLED.name());
            default -> false;
        };
    }

    public static void transition(String from, String to) {
        if (!canTransition(from, to)) throw new ScmBusinessException(ORDER_STATE_INVALID);
    }

    public static void editable(String state) {
        if (!ScmOrderStatusEnum.DRAFT.name().equals(state)) throw new ScmBusinessException(ORDER_STATE_INVALID);
    }

    public static void actualQuantity(String state) {
        if (!ScmOrderStatusEnum.PENDING.name().equals(state)) throw new ScmBusinessException(ORDER_STATE_INVALID);
    }
}
