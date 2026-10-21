/*
 * 原生区域主题同步
 *
 * 页面根节点通过 CSS 变量跟随主题（见 use-mall-theme）。底部导航已改为
 * 自绘（components/common/xsy-tab-bar.vue），图标与文字颜色直接读 --mall-*
 * 变量，不再需要按主题切换图片。
 *
 * 这里只保留原生 TabBar 的颜色同步：自绘导航挂载后原生栏会被隐藏，
 * 但从首屏渲染到隐藏之间仍是原生栏，颜色要对得上，否则会闪一下默认绿。
 * 图标不再下发，避免引用已删除的图片资源。
 */
import { COLOR_TABBAR_NORMAL, MALL_THEMES, normalizeMallThemeCode } from '@/constants/theme-color-const';

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
  } catch {
    // 部分 H5 路由或无 tabBar 环境没有原生区域可同步，静默忽略
  }
}
