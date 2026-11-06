import { mallOrderApi } from '@/api/mall/order-api';
import { mallPromotionApi } from '@/api/mall/promotion-api';

function compareAmount(left, right) {
  const parts = (value) => {
    const match = /^(\d+)(?:\.(\d{1,4}))?$/.exec(String(value ?? ''));
    if (!match) throw new Error('订单金额格式异常，请重新试算');
    return [match[1].replace(/^0+(?=\d)/, ''), (match[2] || '').padEnd(4, '0')];
  };
  const a = parts(left);
  const b = parts(right);
  if (a[0].length !== b[0].length) return a[0].length > b[0].length ? 1 : -1;
  const first = a[0] === b[0] ? 0 : a[0] > b[0] ? 1 : -1;
  return first || (a[1] === b[1] ? 0 : a[1] > b[1] ? 1 : -1);
}

/**
 * 复用服务端完整试算选择优惠券，不在客户端计算让利或占用优惠券。
 * @param {object} form 本次商品、地址与配送仓的请求副本，商品数量保持原四位定点字符串。
 * @param {string} mode AUTO自动择优、MANUAL验证所选券、NONE明确不用券；缺省为AUTO。
 * @param {Function} isCurrent 是否仍为当前请求，过期时停止启动后续试算，调用方仍须丢弃旧结果。
 * @returns {Promise<object>} 正式试算结果及选券资料；网络或金额异常抛出，不能宣称已选择最优方案。
 */
export async function previewCheckout(form, mode = 'AUTO', isCurrent = () => true) {
  const preview = async (couponInstanceId) => (await mallOrderApi.preview({ ...form, couponInstanceId }, { silent: true })).data;
  const selected = mode === 'MANUAL' ? form.couponInstanceId : undefined;
  const data = await preview(selected);
  if (!data || typeof data !== 'object') throw new Error('订单试算结果不完整，请重新获取');
  const result = (value, couponNotice = '') => ({
    data: value,
    couponId: value.promotion?.couponInstanceId || null,
    couponName: value.promotion?.couponName || '',
    couponNotice,
  });
  if (mode === 'MANUAL' && selected && data.promotionError) {
    const fallback = await preview(undefined);
    return result(fallback, `所选优惠券不适用，已按不使用优惠券重新试算：${data.promotionError}`);
  }
  if (mode !== 'AUTO' || data.promotionError || !isCurrent()) return result(data);
  let best = data;
  let pageNum = 1;
  let pages = 1;
  do {
    if (!isCurrent()) return result(best);
    const page = (await mallPromotionApi.myCoupons({ pageNum, pageSize: 20, tab: 'AVAILABLE' }, { silent: true })).data || {};
    pages = Number(page.pages || 0);
    const coupons = (page.list || []).filter((coupon) => coupon.effectiveStatus === 'AVAILABLE');
    // 限制并发，保留完整分页；候选券的门槛、范围与活动叠加均交给正式试算判断。
    for (let index = 0; index < coupons.length && isCurrent(); index += 3) {
      const candidates = await Promise.all(coupons.slice(index, index + 3).map((coupon) => preview(coupon.id)));
      for (const candidate of candidates) {
        if (candidate.priceFingerprint !== data.priceFingerprint) throw new Error('商品价格已更新，请重新试算');
        if (candidate.promotionError || !candidate.promotion?.couponInstanceId) continue;
        // 比较完整优惠后的应付，不能按券面额排序；没有额外优惠时保留不用券，避免浪费。
        if (compareAmount(candidate.promotion.payableAmount, best.promotion?.payableAmount ?? best.estimatedAmount) < 0) best = candidate;
      }
    }
    pageNum += 1;
  } while (pageNum <= pages);
  return result(best);
}
