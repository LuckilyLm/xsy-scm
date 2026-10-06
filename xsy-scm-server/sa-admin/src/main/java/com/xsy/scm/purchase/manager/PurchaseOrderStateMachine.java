package com.xsy.scm.purchase.manager;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;

import java.util.Set;

import static com.xsy.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_STATE_INVALID;

/**
 * 采购单状态机（6 状态）。
 *
 * <p>
 * 实现为声明式转换表，不是散落的 if，与 {@code ck_purchase_order_status} 白名单逐条对应： {@code DRAFT} 提交转 {@code SUBMITTED}，收货确认依次转
 * {@code PARTIALLY_RECEIVED} / {@code RECEIVED}； {@code DRAFT} 与 {@code SUBMITTED} 可取消为
 * {@code CANCELLED}，{@code PARTIALLY_RECEIVED} 只能短关为 {@code SHORT_CLOSED}。
 *
 * <p>
 * 部分收货不可取消，需要终止时走 {@link #shortClosable} 对应的 {@code shortClose}。 {@code RECEIVED} / {@code SHORT_CLOSED} /
 * {@code CANCELLED} 是终态。
 */
public final class PurchaseOrderStateMachine {

    private PurchaseOrderStateMachine() {
    }

    /**
     * 状态图：只有表里列出的 (from, to) 是合法的。
     */
    public static boolean canTransition(String from, String to) {
        ScmPurchaseStatusEnum fromStatus = parseStatus(from);
        ScmPurchaseStatusEnum toStatus = parseStatus(to);
        if (fromStatus == null || toStatus == null) {
            return false;
        }
        return switch (fromStatus) {
            case DRAFT -> Set.of(ScmPurchaseStatusEnum.SUBMITTED, ScmPurchaseStatusEnum.CANCELLED).contains(toStatus);
            case SUBMITTED -> Set.of(ScmPurchaseStatusEnum.PARTIALLY_RECEIVED, ScmPurchaseStatusEnum.RECEIVED,
                    ScmPurchaseStatusEnum.CANCELLED).contains(toStatus);
            // 同一状态到自身是合法的：第二次收货后仍是 PARTIALLY_RECEIVED
            case PARTIALLY_RECEIVED -> Set.of(ScmPurchaseStatusEnum.PARTIALLY_RECEIVED, ScmPurchaseStatusEnum.RECEIVED,
                    ScmPurchaseStatusEnum.SHORT_CLOSED).contains(toStatus);
            default -> false; // RECEIVED / SHORT_CLOSED / CANCELLED 为终态
        };
    }

    private static ScmPurchaseStatusEnum parseStatus(String statusText) {
        if (statusText == null) {
            return null;
        }
        try {
            return ScmPurchaseStatusEnum.valueOf(statusText);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 断言转换合法，否则 40982。
     */
    public static void transition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new ScmBusinessException(PURCHASE_ORDER_STATE_INVALID);
        }
    }

    /**
     * 只有 {@code DRAFT} 可编辑行与需求分配。
     */
    public static boolean editable(String purchaseOrderStatus) {
        return ScmPurchaseStatusEnum.DRAFT.name().equals(purchaseOrderStatus);
    }

    /**
     * 可建 / 可确认收货：{@code SUBMITTED} 或 {@code PARTIALLY_RECEIVED}。
     */
    public static boolean receivable(String purchaseOrderStatus) {
        return ScmPurchaseStatusEnum.SUBMITTED.name().equals(purchaseOrderStatus)
                || ScmPurchaseStatusEnum.PARTIALLY_RECEIVED.name().equals(purchaseOrderStatus);
    }

    /**
     * 可取消：{@code DRAFT} 或 {@code SUBMITTED}。<b>不含</b> {@code PARTIALLY_RECEIVED}。
     */
    public static boolean cancellable(String purchaseOrderStatus) {
        return ScmPurchaseStatusEnum.DRAFT.name().equals(purchaseOrderStatus)
                || ScmPurchaseStatusEnum.SUBMITTED.name().equals(purchaseOrderStatus);
    }

    /**
     * 可少收关单：仅 {@code PARTIALLY_RECEIVED}。
     */
    public static boolean shortClosable(String purchaseOrderStatus) {
        return ScmPurchaseStatusEnum.PARTIALLY_RECEIVED.name().equals(purchaseOrderStatus);
    }

    /**
     * 是否终态（只读）。
     */
    public static boolean terminal(String purchaseOrderStatus) {
        return ScmPurchaseStatusEnum.RECEIVED.name().equals(purchaseOrderStatus)
                || ScmPurchaseStatusEnum.SHORT_CLOSED.name().equals(purchaseOrderStatus)
                || ScmPurchaseStatusEnum.CANCELLED.name().equals(purchaseOrderStatus);
    }

    /**
     * 收货确认后由「全部行是否收齐」推导出的采购单新状态。
     *
     * <p>
     * 注意：<b>已收满的采购单不再回到 {@code PARTIALLY_RECEIVED}</b>；只要所有活动行 {@code received >= planned} 就是 {@code RECEIVED}，否则
     * {@code PARTIALLY_RECEIVED}。
     */
    public static String afterReceipt(boolean allLinesFulfilled) {
        return allLinesFulfilled
                ? ScmPurchaseStatusEnum.RECEIVED.name()
                : ScmPurchaseStatusEnum.PARTIALLY_RECEIVED.name();
    }
}
