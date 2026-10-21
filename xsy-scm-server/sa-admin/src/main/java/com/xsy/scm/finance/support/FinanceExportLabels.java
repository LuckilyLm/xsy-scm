package com.xsy.scm.finance.support;

import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceCounterpartyTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinancePaymentMethodEnum;
import com.xsy.scm.finance.constant.ScmFinancePaymentSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReceiptMethodEnum;
import com.xsy.scm.finance.constant.ScmFinanceSettleStateEnum;

/**
 * 财务导出用的枚举中文标签。
 *
 * <p>
 * 导出文件是给人看的，不能把 {@code NORMAL}、{@code SETTLED} 这类内部取值直接写进单元格。 落库与接口仍用枚举名，两边的对应关系由各枚举的 {@code desc} 承担。
 *
 * <p>
 * 取值不在枚举里时原样返回：宁可让人看到异常值去核查，也不要导出成空白。
 */
public final class FinanceExportLabels {

    private FinanceExportLabels() {
    }

    public static String entryType(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return ScmFinanceEntryTypeEnum.valueOf(value).getDesc();
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    public static String settleState(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return ScmFinanceSettleStateEnum.valueOf(value).getDesc();
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    public static String counterpartyType(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return ScmFinanceCounterpartyTypeEnum.valueOf(value).getDesc();
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    public static String receiptMethod(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return ScmFinanceReceiptMethodEnum.valueOf(value).getDesc();
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    public static String paymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return ScmFinancePaymentMethodEnum.valueOf(value).getDesc();
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    public static String paymentSourceType(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return ScmFinancePaymentSourceTypeEnum.valueOf(value).getDesc();
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    /** 核销导出里的资金类型与目标类型。 */
    public static String businessType(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return ScmFinanceBusinessTypeEnum.valueOf(value).getDesc();
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }
}
