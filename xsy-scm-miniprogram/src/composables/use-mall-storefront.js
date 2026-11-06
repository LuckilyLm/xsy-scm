import { computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useMallStorefrontStore } from '@/store/modules/mall/storefront';

/** 页面显示时刷新店铺资料；返回只读展示状态，保存后的配置在下一次显示时生效。 */
export function useMallStorefront() {
  const store = useMallStorefrontStore();
  onShow(() => store.refresh());
  return { storeName: computed(() => store.storeName), storeLink: computed(() => store.storeLink) };
}
