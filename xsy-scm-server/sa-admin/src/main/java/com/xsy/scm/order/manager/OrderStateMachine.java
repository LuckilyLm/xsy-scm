package com.xsy.scm.order.manager;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_STATE_INVALID;

/**
 * 只管订单生命周期，履约状态不进入这张状态图。
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
            case DRAFT ->
                to.equals(ScmOrderStatusEnum.PENDING.name()) || to.equals(ScmOrderStatusEnum.CANCELLED.name());
            case PENDING ->
                to.equals(ScmOrderStatusEnum.CONFIRMED.name()) || to.equals(ScmOrderStatusEnum.CANCELLED.name());
            default -> false;
        };
    }

    public static void transition(String from, String to) {
        if (!canTransition(from, to))
            throw new ScmBusinessException(ORDER_STATE_INVALID);
    }

    /**
     * 可改单状态：草稿、待确认，以及已确认但还没被锁住或发车拦截的订单。
     *
     * <p>
     * 状态只回答「业务上还能不能改」；已确认订单还要过锁单和发车两道授权闸门，见 {@code SalesOrderService#update}。
     *
     * <p>
     * 待确认与已确认订单的报价已经锁定，改单后由服务层沿用行上已有的报价重新锁定单价； 是否需要按最新价格重算由“批量核价”显式完成，改单本身不静默改价。
     */
    public static void editable(String state) {
        if (!ScmOrderStatusEnum.DRAFT.name().equals(state) && !ScmOrderStatusEnum.PENDING.name().equals(state)
            && !ScmOrderStatusEnum.CONFIRMED.name().equals(state))
            throw new ScmBusinessException(ORDER_STATE_INVALID);
    }

    /**
     * 可以录入实发量的状态：待确认，以及已确认但还没发车出库的订单。
     *
     * <p>
     * 已确认订单的实发量决定结算金额，改之前还要过锁单与发车两道闸门，见 {@code SalesOrderService#actualQuantity}。
     */
    public static void actualQuantity(String state) {
        if (!ScmOrderStatusEnum.PENDING.name().equals(state) && !ScmOrderStatusEnum.CONFIRMED.name().equals(state))
            throw new ScmBusinessException(ORDER_STATE_INVALID);
    }
}
