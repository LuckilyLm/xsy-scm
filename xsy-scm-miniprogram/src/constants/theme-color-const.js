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
export const COLOR_PRIMARY_DARK = '#12863d';
export const COLOR_PRIMARY_LIGHT = '#e8f6ed';
export const COLOR_PRIMARY_DISABLED = '#96d6ae';

/** 语义色 */
export const COLOR_SUCCESS = '#16a34a';
export const COLOR_WARNING = '#b45309';
export const COLOR_DANGER = '#d92d20';
export const COLOR_INFO = '#3b82f6';

/** 文字色 */
export const COLOR_TEXT_PRIMARY = '#1f2329';
export const COLOR_TEXT_SECONDARY = '#646a73';
export const COLOR_TEXT_TERTIARY = '#667085';
export const COLOR_TEXT_PLACEHOLDER = '#667085';
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
function mixColor(color, target, ratio) {
  return (
    '#' +
    [1, 3, 5]
      .map((offset) =>
        Math.round(parseInt(color.slice(offset, offset + 2), 16) * (1 - ratio) + target * ratio)
          .toString(16)
          .padStart(2, '0')
      )
      .join('')
  );
}

// 与服务端 MallThemeColors 使用同一派生比例，默认主题也要和后台预览一致。
function presetTheme(primary) {
  return {
    primary,
    dark: mixColor(primary, 0, 0.18),
    ink: mixColor(primary, 0, 0.32),
    light: mixColor(primary, 255, 0.9),
    disabled: mixColor(primary, 255, 0.55),
  };
}

export const MALL_THEMES = {
  FRESH: presetTheme('#16a34a'),
  WARM: presetTheme('#f97316'),
  OCEAN: presetTheme('#0284c7'),
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

/**
 * 用当前主题色阶生成统一品牌表面，避免业务页和后台预览各自拼出不同渐变。
 * @param {object|null} palette 完整十六进制色阶；为空或非法时使用默认绿色。
 * @returns {{brand: string, action: string, surface: string}} 品牌区、白字主按钮与浅色入口的 CSS 背景。
 */
export function mallThemeGradients(palette) {
  const colors = normalizeMallThemePalette(palette) || MALL_THEMES.FRESH;
  // 主按钮使用深色文字阶，进一步加深起点；浅主色也能承载清晰白字。
  const deep = mixColor(colors.ink, 0, 0.14);
  return {
    brand: `linear-gradient(118deg, ${colors.ink} 0%, ${colors.dark} 48%, ${colors.primary} 100%)`,
    action: `linear-gradient(118deg, ${deep} 0%, ${colors.ink} 100%)`,
    surface: `linear-gradient(110deg, ${colors.light} 0%, #ffffff 100%)`,
  };
}
