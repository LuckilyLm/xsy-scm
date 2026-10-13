import { formatDateTime } from '@/utils/order-display';

export const COUPON_TABS = [
  { value: 'AVAILABLE', label: '可使用' },
  { value: 'USED', label: '已使用' },
  { value: 'INVALID', label: '不可用' },
];

const STATUS_LABELS = {
  AVAILABLE: '可使用',
  RESERVED: '订单处理中',
  USED: '已使用',
  RELEASED: '已释放',
  EXPIRED: '已过期',
  UNAVAILABLE: '暂不可用',
};

export function couponStatusText(status) {
  return STATUS_LABELS[status] || '暂不可用';
}

export function couponBenefit(coupon) {
  if (coupon.discountType === 'RATE') {
    const rate = Number(coupon.discountValue);
    return Number.isFinite(rate) && rate > 0 && rate < 1
      ? `${(rate * 10).toFixed(2).replace(/0+$/, '').replace(/\.$/, '')} 折` : '折扣券';
  }
  return `减 ¥${coupon.discountValue || '—'}`;
}

export function couponThreshold(coupon) {
  return coupon.minOrderAmount && coupon.minOrderAmount !== '0.0000'
    ? `满 ¥${coupon.minOrderAmount} 可用` : '无门槛';
}

export function couponWindow(coupon) {
  return coupon.validTo ? `有效期至 ${formatDateTime(coupon.validTo).slice(0, 10)}` : '有效期以平台规则为准';
}
