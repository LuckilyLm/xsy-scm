package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 收款方式，与付款方式 {@link ScmFinancePaymentMethodEnum} 分开：那个枚举被收款与供应商付款共用，直接加 {@code ONLINE_PAYMENT} 会反向污染付款语义，拆开后付款侧值域与
 * {@code ck_finance_payment_method} 保持原样。
 *
 * <p>
 * 不含 {@code COD}：本字段表示实际收款方式 / 资金渠道，而 COD 是结算时机；货到付款真正收到钱时方式仍是现金或转账。
 *
 * <p>
 * 不含 {@code BALANCE}：余额消费不是新的实际资金进入 —— 充值 100 时渠道已真实收到并记了一条 {@code ONLINE_PAYMENT} 收款，余额买 60 若再生成收款，账上就有 160 而公司只进来
 * 100。钱包余额增减属于权益账本（{@code customer_balance_movement}）。
 *
 * <p>
 * 取值由 Java enum 与 {@code ck_finance_receipt_method} 双向固定，不引入 {@code t_dict}：收款方式一旦能被随意增删，历史收款的方式语义就会漂移。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceReceiptMethodEnum {

    /** 现金。 */
    CASH("现金"),

    /** 银行转账。 */
    BANK_TRANSFER("银行转账"),

    /** 在线支付（经支付渠道收取，来源见 {@code finance_receipt.source_type}）。 */
    ONLINE_PAYMENT("在线支付"),

    /** 其它；具体渠道写在 {@code external_reference} 或 {@code remark} 里。 */
    OTHER("其它");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmFinanceReceiptMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static ScmFinanceReceiptMethodEnum of(String value) {
        for (ScmFinanceReceiptMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
