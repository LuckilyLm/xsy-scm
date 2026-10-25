/*
 * 商城主题全局状态
 *
 * 唯一状态源：themeCode / palette / releaseId / 请求代次。预设主题的颜色映射
 * 登记在 constants/theme-color-const.js；后台配置自定义主色时，服务端返回
 * 完整色阶（primary / dark / ink / light / disabled），这里只做形状校验。
 *
 * 回退规则（统一在 applyTheme 内收口）：
 *   空发布、历史缺失、未知 themeCode、色阶非法、请求失败 → 一律回退 FRESH。
 * 请求代次（requestSeq）用于丢弃乱序返回的旧响应；
 * inFlight 合并同时发生的刷新（冷启动、恢复前台、页面显示可能并发触发）。
 */
import { defineStore } from 'pinia';
import { MALL_THEME_DEFAULT_CODE, MALL_THEMES, normalizeMallThemeCode, normalizeMallThemePalette } from '@/constants/theme-color-const';
import { mallHomeApi } from '@/api/mall/home-api';
import { smartSentry } from '@/lib/smart-sentry';
import { applyNativeTheme } from '@/utils/theme-native';

let inFlight = null;

function themeOf(code) {
  return MALL_THEMES[normalizeMallThemeCode(code)];
}

export const useMallThemeStore = defineStore('mall-theme', {
  state: () => ({
    themeCode: MALL_THEME_DEFAULT_CODE,
    /** 自定义主色派生色阶；为空表示使用 themeCode 对应的预设 */
    palette: null,
    releaseId: null,
    requestSeq: 0,
  }),
  getters: {
    /** 当前主题的完整配色（已归一化，可直接消费） */
    theme: (state) => (state.releaseId != null && state.palette ? state.palette : themeOf(state.themeCode)),
    /** 页面根节点绑定的 CSS 变量；未知编码自动落到默认主题 */
    themeStyle: (state) => {
      const theme = state.releaseId != null && state.palette ? state.palette : themeOf(state.themeCode);
      return {
        '--mall-primary': theme.primary,
        '--mall-primary-dark': theme.dark,
        '--mall-primary-ink': theme.ink,
        '--mall-primary-light': theme.light,
        '--mall-primary-disabled': theme.disabled,
      };
    },
  },
  actions: {
    applyTheme({ releaseId, themeCode, palette }) {
      this.releaseId = releaseId ?? null;
      this.themeCode = releaseId == null ? MALL_THEME_DEFAULT_CODE : normalizeMallThemeCode(themeCode);
      this.palette = releaseId == null ? null : normalizeMallThemePalette(palette);
      applyNativeTheme(this.theme);
    },
    /** 刷新已发布主题；并发调用合并为同一次请求 */
    async refresh() {
      if (inFlight) {
        return inFlight;
      }
      const request = ++this.requestSeq;
      const task = (async () => {
        try {
          const response = await mallHomeApi.theme();
          if (request !== this.requestSeq) {
            return;
          }
          const data = response.data || {};
          this.applyTheme({ releaseId: data.releaseId, themeCode: data.themeCode, palette: data.palette });
        } catch (error) {
          if (request !== this.requestSeq) {
            return;
          }
          smartSentry.captureError(error);
          this.applyTheme({ releaseId: null, themeCode: MALL_THEME_DEFAULT_CODE, palette: null });
        }
      })();
      inFlight = task.finally(() => {
        inFlight = null;
      });
      return inFlight;
    },
  },
});
