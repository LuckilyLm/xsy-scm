package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 收付款方式：第一阶段固定三值，收款与付款**共用同一套枚举**（Q21）。
 *
 * <p>实现方式是 Java enum + DB CHECK，**不引入 SCM {@code t_dict}**（Q21）：
 * 字典表适合运营可维护的取值，而方式一旦能被随意增删，历史收付款的方式语义就会漂移，
 * 且「本期不得出现在线支付」这条边界也无从强制。
 *
 * <p><b>本期不得加入</b> {@code ONLINE_PAYMENT} / {@code BALANCE} / {@code COD} / {@code RECHARGE}
 * 或任何其他 P5 支付能力（Q21、设计稿 §1.2）。要加就得先扩 {@code ck_finance_receipt_method}
 * 与 {@code ck_finance_payment_method} 的白名单（新迁移，不改 V65），并回到
 * {@code docs/decisions.md} 追加裁决。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePaymentMethodEnum {

    /**
     * 现金。
     */
    CASH("现金"),

    /**
     * 银行转账。
     */
    BANK_TRANSFER("银行转账"),

    /**
     * 其它；具体渠道写在 {@code external_reference} / {@code remark} 里，本期不为它建子枚举。
     */
    OTHER("其它");

    private final String desc;
}
