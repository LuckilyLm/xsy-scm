/* 店铺资料只有一个状态源；匿名刷新不会清理客户会话，断网时保留已展示的资料。 */
import { defineStore } from 'pinia';
import { mallDecorationApi } from '@/api/mall/decoration-api';

let inFlight = null;

export const useMallStorefrontStore = defineStore('mall-storefront', {
  state: () => ({ storeName: '鲜蔬源', storeLink: '', requestSeq: 0 }),
  actions: {
    /** 刷新已保存的店铺资料；并发进入页面共用请求，失败保留上次成功值。 */
    async refresh() {
      if (inFlight) return inFlight;
      const request = ++this.requestSeq;
      inFlight = (async () => {
        try {
          const { data } = await mallDecorationApi.store();
          if (request !== this.requestSeq) return;
          this.storeName = data?.storeName?.trim() || '鲜蔬源';
          this.storeLink = /^https:\/\//i.test(data?.storeLink || '') ? data.storeLink : '';
        } catch {
          // 店铺配置读取失败不能阻断商品浏览，也不能用失败结果覆盖已知资料。
        }
      })().finally(() => {
        inFlight = null;
      });
      return inFlight;
    },
  },
});
