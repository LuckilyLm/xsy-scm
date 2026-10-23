/* 全局 loading */
import {defineStore} from 'pinia';
import {smartSentry} from '/@/lib/smart-sentry';

export const useSpinStore = defineStore({
  id: 'spin',
  state: () => ({
    loading: false,
  }),

  actions: {
    hide() {
      this.loading = false;
      // 调整遮罩层级，避免盖住弹窗
      try {
        const spins = document.querySelector('.ant-spin-nested-loading');
        if (spins) {
          spins.style.zIndex = '999';
        }
      } catch (error) {
        smartSentry.captureError('Spin hide操作失败:', error);
      }
    },
    show() {
      this.loading = true;
      // 调整遮罩层级，避免盖住弹窗
      try {
        const spins = document.querySelector('.ant-spin-nested-loading');
        if (spins) {
          spins.style.zIndex = '1001';
        }
      } catch (error) {
        smartSentry.captureError('Spin hide操作失败:', error);
      }
    },
  },
});
