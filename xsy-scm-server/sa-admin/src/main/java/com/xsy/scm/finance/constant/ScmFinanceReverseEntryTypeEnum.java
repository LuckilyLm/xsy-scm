package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 错误的收款、付款或核销不能修改原记录，只能新增带原因的反向记录。一笔原始记录最多反向一次；红字应收应付与反向登记不是同一业务。 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceReverseEntryTypeEnum {

    /**
     * 原始事实：{@code reverse_of_id} 必须为 {@code null}。
     */
    NORMAL("正常"),

    /**
     * 反向事实：{@code reverse_of_id} 必填且指向同表的 {@code NORMAL} 行，{@code reason} 必填非空。
     */
    REVERSE("反向");

    private final String desc;
}
