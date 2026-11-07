/*
 * 原生区域主题同步
 *
 * 页面根节点通过 CSS 变量跟随主题（见 use-mall-theme）。自绘底部导航的
 * 选中文字读取主题，交付PNG保留原色。原生弹窗无法继承页面CSS变量，
 * 通过统一拦截在打开时读取最新配色，避免业务页遗漏或保留旧颜色。
 *
 * 同时同步原生 TabBar 颜色：自绘导航挂载后原生栏会被隐藏，
 * 但从首屏渲染到隐藏之间仍是原生栏，颜色要对得上，否则会闪一下默认绿。
 * 图标不再下发，避免引用已删除的图片资源。
 */
import { COLOR_TABBAR_NORMAL, MALL_THEMES, normalizeMallThemeCode } from '@/constants/theme-color-const';

let currentPalette = MALL_THEMES.FRESH;
let modalInstalled = false;

function noop() {}

/**
 * 同步应用可控的原生品牌色，弹窗拦截只注册一次。
 * @param {object|null} theme 当前色阶；缺失时使用默认绿色，弹窗优先使用可读的ink。
 * @returns {void} 原生API不可用时忽略TabBar更新，不影响页面操作。
 */
export function applyNativeTheme(theme) {
  const palette = theme && typeof theme.primary === 'string' ? theme : MALL_THEMES[normalizeMallThemeCode()];
  currentPalette = palette;
  if (!modalInstalled) {
    uni.addInterceptor('showModal', {
      invoke(options) {
        // 显式指定的危险等语义色保留；不包裹回调，也不改变原API的Promise行为。
        if (!options.confirmColor) options.confirmColor = currentPalette.ink || currentPalette.primary;
      },
    });
    modalInstalled = true;
  }
  try {
    uni.setTabBarStyle({
      color: COLOR_TABBAR_NORMAL,
      selectedColor: palette.primary,
      backgroundColor: '#ffffff',
      borderStyle: 'white',
      fail: noop,
    });
  } catch {
    // 部分 H5 路由或无 tabBar 环境没有原生区域可同步，静默忽略
  }
}
