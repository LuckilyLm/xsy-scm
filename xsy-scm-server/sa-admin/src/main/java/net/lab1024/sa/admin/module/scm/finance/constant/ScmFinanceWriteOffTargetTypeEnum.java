package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 核销行的目标侧类型；<b>数据范围随本列</b>（D-5）：{@code RECEIVABLE} 走 orderSellerScope，
 * {@code PAYABLE} 走 purchaserScope。
 *
 * <p>即「核销行的可见性 = 被核销单据的可见性」。核销行本身不是独立归属对象，
 * 因此只看得到收款、看不到某张应收的角色，在该应收抽屉里也看不到指向它的核销行。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceWriteOffTargetTypeEnum {

    /**
     * 应收：{@code target_id} = {@code finance_receivable.id}。
     */
    RECEIVABLE("应收"),

    /**
     * 应付：{@code target_id} = {@code finance_payable.id}。
     */
    PAYABLE("应付");

    private final String desc;
}
