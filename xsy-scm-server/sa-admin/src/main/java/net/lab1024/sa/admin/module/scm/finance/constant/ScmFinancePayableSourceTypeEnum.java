package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 应付单头的来源类型，与 {@link ScmFinanceEntryTypeEnum} 由
 * {@code ck_finance_payable_source_pairing} 在库级配对。
 *
 * <p>{@code MANUAL} 只服务手工红字应付（Q13）。本期**不**为它接任何自动业务来源 ——
 * 采购退货全库不存在，第二批 Q13 已裁不新建采购退货模块；若未来采购退货进入正式需求，
 * 它可以成为红字应付的另一个来源，但那需要新裁决与新迁移。
 *
 * <p>{@code MANUAL} 行的 {@code source_id} 必须为 {@code NULL}，这让它落在
 * {@code uk_finance_payable_source_active} 的 {@code source_id IS NOT NULL} 谓词之外；
 * 其防重由「可冲上限（41137）+ 请求级幂等键」承担，与手工出库单同形。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinancePayableSourceTypeEnum {

    /**
     * 正常应付：{@code source_id} = {@code purchase_receipt.id}。
     */
    PURCHASE_RECEIPT("采购收货单"),

    /**
     * 手工红字应付：{@code source_id} 必须为 {@code null}，{@code original_payable_id} 与
     * {@code reason} 必填。
     */
    MANUAL("手工登记");

    private final String desc;
}
