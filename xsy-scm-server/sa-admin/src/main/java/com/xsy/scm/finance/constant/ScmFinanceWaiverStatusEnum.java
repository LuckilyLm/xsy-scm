package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 抹零在接口层的状态取值。
 *
 * <p>
 * 不落库：是否已反向由该抹零的核销行有没有 {@code REVERSE} 推导（{@code reverse_of_id} 唯一）， 抹零表只存金额与原因。没有「删除」：抹零错了只能追加反向。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceWaiverStatusEnum {

    /**
     * 有效，金额已从应收未结额中冲减。
     */
    NORMAL("有效"),

    /**
     * 已反向，原事实保留但不再冲减余额。
     */
    REVERSED("已反向");

    private final String desc;
}
