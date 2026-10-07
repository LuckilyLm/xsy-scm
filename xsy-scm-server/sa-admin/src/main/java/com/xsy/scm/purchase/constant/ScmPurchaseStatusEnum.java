package com.xsy.scm.purchase.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 采购单状态（6 值）。
 *
 * <p>
 * 取值与 {@code ck_purchase_order_status} 的白名单一致。
 *
 * <p>
 * 终态为 {@code RECEIVED} / {@code SHORT_CLOSED} / {@code CANCELLED}； {@code PARTIALLY_RECEIVED} 不允许 cancel，需要终止时使用
 * {@code shortClose}。
 */
public enum ScmPurchaseStatusEnum {
    DRAFT,
    SUBMITTED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    SHORT_CLOSED,
    CANCELLED;

    /**
     * 是否已进入正式采购履约链路（「已提交」口径，提交时间见 {@code submitted_at}）。
     *
     * <p>
     * <b>用排除法而不是正列举</b>：新增状态（如 {@code CLOSED} / {@code PARTIALLY_CLOSED}）时，正列举会<b>静默漏算</b> ——
     * 数字看起来正常，只是偏小，而这是本项目最忌讳的失败方式。排除法默认把新状态算进履约链路。
     *
     * <p>
     * 依据 {@code PurchaseOrderStateMachine}：只有 {@code DRAFT} 与 {@code SUBMITTED} 可取消，{@code PARTIALLY_RECEIVED} 只能
     * {@code shortClose}。所以「已提交后又被取消」只发生在一票货都没收的单上 —— 这类单从未产生收货，不计入采购额是对的。
     */
    public boolean committed() {
        return this != DRAFT && this != CANCELLED;
    }

    /**
     * 已提交状态名清单，供 SQL 的 {@code IN} 使用。
     *
     * <p>
     * 采购口径的唯一来源：报表、大屏、首页都从这里取，不要在各自的 SQL 里抄一份状态数组。
     */
    public static List<String> committedNames() {
        return Arrays.stream(values()).filter(ScmPurchaseStatusEnum::committed).map(Enum::name).toList();
    }
}
