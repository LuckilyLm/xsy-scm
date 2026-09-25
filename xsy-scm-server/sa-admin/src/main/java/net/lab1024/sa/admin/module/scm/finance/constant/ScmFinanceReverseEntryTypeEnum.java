package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 收款 / 付款 / 核销的方向：{@code NORMAL} 原始事实，{@code REVERSE} 反向事实。
 *
 * <p>反向事实是 append-only 纪律下**唯一**的纠错手段（全局不变量 1/2、Q18、D-3）：
 * 登错的收付款、分错的核销都不能改原行，只能新增一条引用原行、带原因、金额同样为正的反向行。
 * 一条 {@code NORMAL} 最多被反向一次，由 {@code uk_finance_*_single_reverse} 在库级强制。
 *
 * <p>与应收应付的 {@link ScmFinanceEntryTypeEnum}（{@code NORMAL / RED}）刻意分开：
 * 「红冲一张单据」与「反向一条登记 / 分配」是不同的业务动作，各有各的配对 CHECK。
 */
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
