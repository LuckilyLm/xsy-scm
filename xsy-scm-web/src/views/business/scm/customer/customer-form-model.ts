/*
 * 客户表单模型（纯函数，可被 `node --test` 直接单测）。
 *
 * 关键业务规则（与后端一致）：
 * - 账期三种形态互斥且穷尽：不设置 / 按金额 / 按时间（后端 `ck_customer_credit_period`）；
 * - 单位为「月」时结算日限 1–28（保证 2 月也存在该日期）；
 * - 金额一律是字符串形式的 4 位定点数，绝不做 `Number()` 运算。
 */
import type {CreditPeriodType, CustomerFormModel, CustomerPayload} from '/@/types/business/scm/customer';

/** 非负定点数：最多 14 位整数 + 最多 4 位小数（与后端 `ScmDecimalStrings.PATTERN` 同构）。 */
const DECIMAL = /^\d{1,14}(\.\d{1,4})?$/;

/** 宽松手机号 / 固话校验。 */
const PHONE = /^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$/;

export function emptyCustomer(): CustomerFormModel {
    return {
        // 区划六列（`AreaColumns`）只能显式列出：本模块要能被 `node --test` 直接加载，
        // 不能有相对值导入。少一个键就会让上一条记录的区划串进新建的客户。
        longitude: null,
        latitude: null,
        geomCrs: null,
        provinceCode: null,
        provinceName: null,
        cityCode: null,
        cityName: null,
        districtCode: null,
        districtName: null,
        customerCode: '',
        name: '',
        customerTypeId: undefined,
        parentCustomerId: undefined,
        sellerId: undefined,
        supplierId: undefined,
        contactName: '',
        contactPhone: '',
        address: '',
        settleMode: 'INDEPENDENT',
        creditLimit: '0.0000',
        creditPeriodType: undefined,
        creditAmountThreshold: undefined,
        creditPeriodValue: undefined,
        creditPeriodUnit: undefined,
        settleDay: undefined,
        remark: '',
    };
}

/**
 * 切换账期类型时清掉不属于该形态的字段。
 *
 * 不做清理的话，「按金额」的行会残留上一次「按时间」的账期值，后端会直接判为非法组合（40000）。
 */
export function applyCreditPeriodType(form: CustomerFormModel, type?: CreditPeriodType | null): CustomerFormModel {
    const next: CustomerFormModel = {...form, creditPeriodType: type ?? undefined};
    if (type !== 'BY_AMOUNT') {
        next.creditAmountThreshold = undefined;
    }
    if (type !== 'BY_TIME') {
        next.creditPeriodValue = undefined;
        next.creditPeriodUnit = undefined;
        next.settleDay = undefined;
    } else {
        next.creditPeriodUnit = next.creditPeriodUnit ?? 'DAY';
    }
    if (next.creditPeriodUnit !== 'MONTH') {
        next.settleDay = undefined;
    }
    return next;
}

/** 单位切到「天」时清掉结算日（按天账期没有结算日概念）。 */
export function applyCreditPeriodUnit(form: CustomerFormModel, unit?: 'DAY' | 'MONTH' | null): CustomerFormModel {
    const next: CustomerFormModel = {...form, creditPeriodUnit: unit ?? undefined};
    if (unit !== 'MONTH') {
        next.settleDay = undefined;
    }
    return next;
}

export function validateCustomer(form: CustomerFormModel): string | undefined {
    if (!form.name?.trim()) {
        return '请输入客户名称';
    }
    if (!form.customerTypeId) {
        return '请选择客户类型';
    }
    const phone = form.contactPhone?.trim();
    if (phone && !PHONE.test(phone)) {
        return '联系电话格式不正确';
    }
    const creditLimit = form.creditLimit?.trim();
    if (creditLimit && !DECIMAL.test(creditLimit)) {
        return '授信额度须为非负数，最多四位小数';
    }
    if (form.creditPeriodType === 'BY_AMOUNT') {
        const threshold = form.creditAmountThreshold?.trim();
        if (!threshold) {
            return '按金额账期必须填写金额阈值';
        }
        if (!DECIMAL.test(threshold)) {
            return '金额阈值须为非负数，最多四位小数';
        }
    }
    if (form.creditPeriodType === 'BY_TIME') {
        if (!form.creditPeriodValue || form.creditPeriodValue <= 0) {
            return '按时间账期必须填写大于 0 的账期值';
        }
        if (!form.creditPeriodUnit) {
            return '请选择账期单位';
        }
        if (form.creditPeriodUnit === 'MONTH' && form.settleDay != null && (form.settleDay < 1 || form.settleDay > 28)) {
            return '固定结算日必须在 1 到 28 之间';
        }
    }
    return undefined;
}

/**
 * 提交前归一化 → 写请求体。
 *
 * 空白字符串统一转成 `null`：后端把「空白」也视作未填写，但如果前端直接送 `""`，
 * 就会出现「想清空却清不掉」的错觉（后端 `FieldStrategy.ALWAYS` 只对 `null` 生效）。
 *
 * 编码在这里被摘掉：它是服务端生成的只读回显，返回类型 `CustomerPayload` 上根本没有这个键，
 * 所以「客户端 × 编码」是类型层面的事实，不是靠后端忽略。
 */
export function toCustomerPayload(form: CustomerFormModel): CustomerPayload {
    const blankToNull = (value?: string | null): string | null => {
        const text = value == null ? '' : String(value).trim();
        return text === '' ? null : text;
    };
    const {customerCode, ...rest} = form;
    return {
        ...rest,
        name: (form.name ?? '').trim(),
        parentCustomerId: form.parentCustomerId ?? null,
        sellerId: form.sellerId ?? null,
        supplierId: form.supplierId ?? null,
        contactName: blankToNull(form.contactName),
        contactPhone: blankToNull(form.contactPhone),
        address: blankToNull(form.address),
        remark: blankToNull(form.remark),
        creditLimit: blankToNull(form.creditLimit),
        creditPeriodType: form.creditPeriodType ?? null,
        creditAmountThreshold: blankToNull(form.creditAmountThreshold),
        creditPeriodValue: form.creditPeriodValue ?? null,
        creditPeriodUnit: form.creditPeriodUnit ?? null,
        settleDay: form.settleDay ?? null,
    };
}
