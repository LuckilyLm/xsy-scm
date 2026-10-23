/*
 * 加购动作的唯一实现点。
 *
 * 商品卡片与详情页都调它，避免两处各写一份「加购 + 提示」而出现行为漂移。
 * 不做本地乐观写入：购物车以服务端返回的整车快照为准（跨端一致）。
 */
import { mallCartApi } from '@/api/mall/cart-api';
import { smartSentry } from '@/lib/smart-sentry';

export function useMallCart() {
  /**
   * 加入购物车
   * @param {{skuId: number, minOrderQty?: string|number}} product
   * @param {string|number} [quantity] 字符串定点；不传时由服务器按起订量或递增量快捷加购
   * @returns {Promise<boolean>} 是否成功
   */
  async function addToCart(product, quantity) {
    try {
      const effective = quantity ?? product?.minOrderQty ?? '1';
      const response = await mallCartApi.addItem({
        skuId: product.skuId,
        quantity: String(effective),
        quickAdd: quantity == null,
      });
      const count = response.data?.availableCount ?? 0;
      uni.showToast({ title: count ? `已加入购物车（可结算 ${count} 项）` : '已加入购物车', icon: 'none' });
      return true;
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '加入购物车失败', icon: 'none' });
      return false;
    }
  }

  return { addToCart };
}
