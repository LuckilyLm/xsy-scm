/*
 * 原生区域主题同步
 *
 * 小程序的原生 TabBar 与 page 节点不继承 CSS 变量，必须在主题切换时用
 * uni API 单独同步。CSS 变量注入仍由各页面根节点承担（见 use-mall-theme）。
 * FRESH 沿用 pages.json 中既有的图标资源；其他主题使用同构着色的变体图标
 * （static/images/tabbar/<name>-h-<theme>.png）。
 * 页面显示时重新应用：从登录或子包直达 TabBar 时，上次调用可能尚无原生 TabBar。
 */
import { COLOR_TABBAR_NORMAL, MALL_THEMES, normalizeMallThemeCode } from '@/constants/theme-color-const';



const TAB_ICONS = ['home', 'category', 'cart', 'order', 'mine'];

function noop() {}

export function applyNativeTheme(themeCode) {
  const code = normalizeMallThemeCode(themeCode);
  const theme = MALL_THEMES[code];
  try {
    uni.setTabBarStyle({
      color: COLOR_TABBAR_NORMAL,
      selectedColor: theme.primary,
      backgroundColor: '#ffffff',
      borderStyle: 'white',
      fail: noop,
    });
    TAB_ICONS.forEach((name, index) => {
      const suffix = code === 'FRESH' ? '' : `-${code.toLowerCase()}`;
      uni.setTabBarItem({
        index,
        selectedIconPath: `static/images/tabbar/${name}-h${suffix}.png`,
        fail: noop,
      });
    });
  } catch {
    // 部分 H5 路由或无 tabBar 环境没有原生区域可同步，静默忽略
  }
}
