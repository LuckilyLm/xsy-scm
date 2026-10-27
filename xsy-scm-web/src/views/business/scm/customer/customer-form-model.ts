/** 客户表单模型：纯函数，可直接单测。账期三形态互斥，销售金额按两位录入、提交转四位。 */
import Decimal from 'decimal.js';
import type {CreditPeriodType, CustomerFormModel, CustomerPayload} from '/@/types/business/scm/customer';

/** 销售金额允许两位新录入值，也放行历史四位值；三位和五位以上都拒绝。 */
function isSalesMoney(value: string | number | null | undefined): boolean {
  if (value === null || value === undefined || value === '') return false;
  try {
    const parsed = new Decimal(value);
    const places = parsed.decimalPlaces();
    return parsed.isFinite() && !parsed.isNegative() && (places <= 2 || places === 4);
  } catch {
    return false;
  }
}

/** 本模块会被 node 直接加载，不能使用相对路径的值导入。 */
function roundScale(value: string | number | null | undefined, scale: number): string {
  if (value === null || value === undefined || value === '') return '';
  try {
    return new Decimal(value).toDecimalPlaces(scale, Decimal.ROUND_HALF_UP).toFixed(scale);
  } catch {
    return '';
  }
}

function moneyPayload(value: string | number | null | undefined): string {
  const normalized = roundScale(value, 2);
  return normalized === '' ? '' : roundScale(normalized, 4);
}

/** 宽松手机号 / 固话校验。 */
const PHONE = /^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$/;

export function emptyCustomer(): CustomerFormModel {
  return {
    // 区划等字段要显式列全，避免上一条记录的值串进新建客户。
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
    businessCategoryId: null,
    parentCustomerId: undefined,
    mallDeliveryWarehouseId: null,
    clearMallDeliveryWarehouse: false,
    sellerId: undefined,
    supplierId: undefined,
    contactName: '',
    contactPhone: '',
    address: '',
    settleMode: 'INDEPENDENT',
    codAllowed: false,
    sharedSettlementEnabled: false,
    creditLimit: '0.0000',
    creditPeriodType: undefined,
    creditAmountThreshold: undefined,
    creditPeriodValue: undefined,
    creditPeriodUnit: undefined,
    settleDay: undefined,
    remark: '',
  };
}

/** 切换账期类型时清掉不属于该形态的字段，避免后端判为非法组合。 */
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

/** 单位切到「天」时清掉结算日。 */
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
  if (form.creditLimit?.trim() && !isSalesMoney(form.creditLimit)) {
    return '授信额度须为非负数，最多两位小数';
  }
  if (form.creditPeriodType === 'BY_AMOUNT') {
    if (!form.creditAmountThreshold?.trim()) {
      return '按金额账期必须填写金额阈值';
    }
    if (!isSalesMoney(form.creditAmountThreshold)) {
      return '金额阈值须为非负数，最多两位小数';
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

/** 提交前把空字符串转 null，便于清空字段；客户编码由服务端生成，不提交。 */
export function toCustomerPayload(form: CustomerFormModel): CustomerPayload {
  const blankToNull = (value?: string | null): string | null => {
    const text = value == null ? '' : String(value).trim();
    return text === '' ? null : text;
  };
  const {customerCode, ...rest} = form;
  return {
    ...rest,
    name: (form.name ?? '').trim(),
    businessCategoryId: form.businessCategoryId ?? null,
    parentCustomerId: form.parentCustomerId ?? null,
    sellerId: form.sellerId ?? null,
    supplierId: form.supplierId ?? null,
    contactName: blankToNull(form.contactName),
    contactPhone: blankToNull(form.contactPhone),
    address: blankToNull(form.address),
    remark: blankToNull(form.remark),
    creditLimit: blankToNull(moneyPayload(form.creditLimit)),
    codAllowed: form.codAllowed === true,
    // 只有集团结算才可能共享结算，其余情况置假
    sharedSettlementEnabled: form.settleMode === 'GROUP' && form.sharedSettlementEnabled === true,
    creditPeriodType: form.creditPeriodType ?? null,
    creditAmountThreshold: blankToNull(moneyPayload(form.creditAmountThreshold)),
    creditPeriodValue: form.creditPeriodValue ?? null,
    creditPeriodUnit: form.creditPeriodUnit ?? null,
    settleDay: form.settleDay ?? null,
  };
}
