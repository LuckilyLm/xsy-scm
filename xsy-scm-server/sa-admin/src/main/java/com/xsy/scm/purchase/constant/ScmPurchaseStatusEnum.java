package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * 采购单状态。
 *
 * <p>
 * 取值与 {@code ck_purchase_order_status} 一致。
 *
 * <p>
 * 终态为 {@code ACCOUNTED} / {@code RECEIVED} / {@code SHORT_CLOSED} / {@code CANCELLED}； {@code PARTIALLY_RECEIVED} 不允许
 * cancel，需要终止时使用 {@code shortClose}。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPurchaseStatusEnum {
    DRAFT("草稿"),
    SUBMITTED("已提交"),
    ACCOUNTED("采购已确认"),
    PARTIALLY_RECEIVED("部分收货"),
    RECEIVED("已收货"),
    SHORT_CLOSED("少收关单"),
    CANCELLED("已取消");

    /**
     * 界面与导出用的中文名，与前端 {@code SCM_PURCHASE_STATUS_ENUM} 的 desc 逐字一致。
     *
     * <p>
     * 服务端生成的导出 xlsx 和打印件不经过前端，只能由后端给出中文。
     */
    private final String desc;

    /**
     * 按枚举名解析；未知值返回 {@code null}，由调用方决定回落策略。
     */
    public static ScmPurchaseStatusEnum of(String value) {
        for (ScmPurchaseStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 是否进入正式采购流程必须逐个列出，不能用「排除草稿和取消」倒推；新增状态时要明确是否计入。
     */
    public boolean committed() {
        return switch (this) {
            case SUBMITTED, ACCOUNTED, PARTIALLY_RECEIVED, RECEIVED, SHORT_CLOSED -> true;
            case DRAFT, CANCELLED -> false;
        };
    }

    /**
     * 已提交状态名清单，供 SQL 的 {@code IN} 使用。
     *
     * <p>
     * 报表、大屏、首页都从这里取，不要在各自的 SQL 里另抄一份状态数组。
     */
    public static List<String> committedNames() {
        return Arrays.stream(values()).filter(ScmPurchaseStatusEnum::committed).map(Enum::name).toList();
    }
}
