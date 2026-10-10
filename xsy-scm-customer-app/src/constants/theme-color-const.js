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
export const COLOR_WARNING = '#f59e0b';
export const COLOR_DANGER = '#e5484d';
export const COLOR_INFO = '#3b82f6';

/** 文字色 */
export const COLOR_TEXT_PRIMARY = '#1f2329';
export const COLOR_TEXT_SECONDARY = '#646a73';
export const COLOR_TEXT_TERTIARY = '#8f959e';
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
