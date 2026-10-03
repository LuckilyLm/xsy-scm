package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 收款方式：**收款专用**，与供应商付款的 {@link ScmFinancePaymentMethodEnum} 分开。
 *
 * <p>
 * <b>为什么拆开</b>：那个枚举被收款与供应商付款共用。若直接加 {@code ONLINE_PAYMENT}，
 * 供应商付款入口也会一并获得这个取值 —— 支付这个新业务域就反向污染了供应商付款的语义。
 * 拆开之后，付款侧的值域与 {@code ck_finance_payment_method} 保持原样。
 *
 * <p>
 * <b>为什么没有 COD</b>：这个字段表示「实际收款方式 / 资金渠道」，而 COD 是「什么时候收钱」的
 * **结算时机**。货到付款真正收到钱时，方式仍然是现金或转账之一；把 COD 塞进来会让
 * 「这笔钱是怎么进来的」这个问题失去答案。
 *
 * <p>
 * <b>为什么没有 BALANCE</b>（ADM-12 3-12a 撤销的预埋口径）：余额消费**不是新的实际资金进入**。
 * 客户充值 100 时渠道真实收到 100，已经有一条 {@code ONLINE_PAYMENT} 收款；之后用余额买 60
 * 若再生成一条收款，账上就有 160 —— 而公司实际只进来 100。钱包余额的增减属于**权益账本**
 * （{@code customer_balance_movement}），不是资金动作，两者不能互相替代。
 *
 * <p>
 * 取值由 Java enum 与 {@code ck_finance_receipt_method} 双向固定，**不引入 {@code t_dict}**：
 * 收款方式一旦能被随意增删，历史收款的方式语义就会漂移。
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
