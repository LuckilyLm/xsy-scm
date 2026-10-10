package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
@Getter
@RequiredArgsConstructor
public enum ScmPurchaseStatusEnum {
    DRAFT("草稿"),
    SUBMITTED("已提交"),
    PARTIALLY_RECEIVED("部分收货"),
    RECEIVED("已收货"),
    SHORT_CLOSED("少收关单"),
    CANCELLED("已取消");

    /**
     * 界面与导出用的中文名，与前端 {@code SCM_PURCHASE_STATUS_ENUM} 的 desc 逐字一致。
     *
     * <p>
     * 服务端生成的产物（导出 xlsx、打印件）没有前端可翻译，只能由后端给出中文； 枚举名 {@code SUBMITTED} 对业务用户没有意义。
     */
    private final String desc;

    /** 按枚举名解析；未知值返回 {@code null}，由调用方决定回落策略。 */
    public static ScmPurchaseStatusEnum of(String value) {
        for (ScmPurchaseStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 是否已进入正式采购履约链路（「已提交」口径，提交时间见 {@code submitted_at}）。
     *
     * <p>
     * <b>穷尽 switch、不写 default</b>：enum 一旦新增状态（如 {@code PENDING_APPROVAL} / {@code REJECTED} / {@code CLOSED}），
     * 这里会直接编译不过，逼着开发者显式裁决新状态算不算已提交。
     *
     * <p>
     * 不用「排除 DRAFT 与 CANCELLED」的写法：那样确实不会漏算新状态，但会把<b>尚未进入履约</b>的状态 （审批中、已驳回、备货中）默认算进来，而这类错法同样是静默的 —— 数字看起来正常，只是偏大。
     *
     * <p>
     * 依据 {@code PurchaseOrderStateMachine}：只有 {@code DRAFT} 与 {@code SUBMITTED} 可取消，{@code PARTIALLY_RECEIVED} 只能
     * {@code shortClose}。所以「已提交后又被取消」只发生在一票货都没收的单上 —— 这类单从未产生收货，不计入采购额是对的。
     */
    public boolean committed() {
        return switch (this) {
            case SUBMITTED, PARTIALLY_RECEIVED, RECEIVED, SHORT_CLOSED -> true;
            case DRAFT, CANCELLED -> false;
        };
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
