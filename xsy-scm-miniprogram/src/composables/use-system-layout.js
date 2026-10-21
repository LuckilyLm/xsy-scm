/*
 * 系统窗口布局信息（状态栏 / 导航栏 / 胶囊按钮）
 *
 * 自定义导航栏（navigationStyle: custom）的页面必须自己避开状态栏，
 * 小程序端还要与右上角胶囊按钮垂直居中对齐。这套计算只写一次，
 * 由 xsy-nav-bar 与需要自定义头部的页面共用。
 *
 * 用法：
 *   const { statusBarHeight, navBarHeight } = useSystemLayout();
 */
import { ref } from 'vue';

let cached = null;

function resolve() {
  if (cached) {
    return cached;
  }

  let statusBarHeight = 20;
  let navBarHeight = 44;
  let menuButtonRect = null;

  try {
    const win = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync();
    statusBarHeight = win.statusBarHeight || statusBarHeight;
  } catch {
    // 取不到窗口信息时使用安全默认值
  }

  // #ifdef MP-WEIXIN
  try {
    if (typeof uni.getMenuButtonBoundingClientRect === 'function') {
      menuButtonRect = uni.getMenuButtonBoundingClientRect();
      if (menuButtonRect && menuButtonRect.height) {
        // 胶囊上间距 == 下间距时导航栏才是居中的，据此反推高度
        navBarHeight = (menuButtonRect.top - statusBarHeight) * 2 + menuButtonRect.height;
      }
    }
  } catch {
    // 非微信环境或接口不可用时保持默认 44
  }
  // #endif

  cached = { statusBarHeight, navBarHeight, menuButtonRect };
  return cached;
}

export function useSystemLayout() {
  const layout = resolve();
  return {
    statusBarHeight: ref(layout.statusBarHeight),
    navBarHeight: ref(layout.navBarHeight),
    menuButtonRect: ref(layout.menuButtonRect),
  };
}
