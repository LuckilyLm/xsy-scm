/*
 * JS 侧颜色常量
 *
 * 与 src/styles/tokens.scss 一一对应，供无法使用 SCSS 变量的场景引用，
 * 例如 canvas 绘制、uni.setNavigationBarColor、图表配置等。
 *
 * 修改颜色时**必须同时改这两处**，否则 SCSS 与 JS 会不一致。
 * 之所以不通过 `:export` 从 SCSS 导出：那是 webpack css-loader 的特性，
 * Vite 不支持，写出来只会变成非法 CSS 并触发构建警告。
 */

/** 品牌色 */
export const COLOR_PRIMARY = '#16a34a';
export const COLOR_PRIMARY_DARK = '#12833c';
export const COLOR_PRIMARY_LIGHT = '#e8f5ec';
export const COLOR_PRIMARY_DISABLED = '#a7d8b9';

/** 语义色 */
export const COLOR_SUCCESS = '#16a34a';
export const COLOR_WARNING = '#b45309';
export const COLOR_DANGER = '#d92d20';
export const COLOR_INFO = '#3b82f6';

/** 文字色 */
export const COLOR_TEXT_PRIMARY = '#1f2329';
export const COLOR_TEXT_SECONDARY = '#646a73';
export const COLOR_TEXT_TERTIARY = '#667085';
export const COLOR_TEXT_PLACEHOLDER = '#bbbfc4';
export const COLOR_TEXT_INVERSE = '#ffffff';

/** 背景色 */
export const COLOR_BG_PAGE = '#f5f6f8';
export const COLOR_BG_CARD = '#ffffff';
export const COLOR_BG_HOVER = '#f2f3f5';

/** 边框与分割线 */
export const COLOR_BORDER = '#e5e6eb';
export const COLOR_DIVIDER = '#f0f1f3';

/** TabBar 配色（须与 src/pages.json 的 tabBar 保持一致） */
export const COLOR_TABBAR_NORMAL = '#8f959e';
export const COLOR_TABBAR_SELECTED = '#16a34a';

/*
 * ============================ 商城主题 ============================
 *
 * 主题只换品牌配色（主色 / 深色 / 浅色 / 禁用态），语义色（成功、警告、危险）不随主题变化。
 * themeCode 来自首页发布记录（GET /scm/mall/home/theme），由全局 Pinia 状态归一化后使用。
 * 全局状态见 store/modules/mall/theme.js；页面注入见 composables/use-mall-theme.js。
 */

/** 默认主题（空发布、历史缺失、未知值、请求失败时的统一回退） */
export const MALL_THEME_DEFAULT_CODE = 'FRESH';

/** 已发布可选主题：key 与后台装修的主题编码一一对应 */
export const MALL_THEMES = {
  FRESH: { primary: '#16a34a', dark: '#12833c', ink: '#12833c', light: '#e8f5ec', disabled: '#a7d8b9' },
  WARM: { primary: '#f97316', dark: '#ea580c', ink: '#c2410c', light: '#fff1e6', disabled: '#fdba74' },
  OCEAN: { primary: '#0284c7', dark: '#0369a1', ink: '#0369a1', light: '#e6f4fb', disabled: '#8ec7ec' },
};

/** 未知 / 空值一律回退默认主题，页面不得直接信任服务端返回的 themeCode */
export function normalizeMallThemeCode(code) {
  return code && Object.prototype.hasOwnProperty.call(MALL_THEMES, code) ? code : MALL_THEME_DEFAULT_CODE;
}

const THEME_COLOR_KEYS = ['primary', 'dark', 'ink', 'light', 'disabled'];
const THEME_HEX = /^#[0-9a-fA-F]{6}$/;

/**
 * 自定义主色的派生色阶由服务端统一计算（见 MallThemeColors）。
 * 这里只做形状校验：任何一项缺失或非法就整组丢弃，回退到预设主题，
 * 避免出现「主色是自定义、深色还是默认绿」的混搭。
 */
export function normalizeMallThemePalette(palette) {
  if (!palette || typeof palette !== 'object') return null;
  const normalized = {};
  for (const key of THEME_COLOR_KEYS) {
    const value = palette[key];
    if (typeof value !== 'string' || !THEME_HEX.test(value)) return null;
    normalized[key] = value.toLowerCase();
  }
  return normalized;
}
