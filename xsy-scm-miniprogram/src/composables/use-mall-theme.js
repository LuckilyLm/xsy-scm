/*
 * 页面级主题注入
 *
 * 每个页面根节点绑定 :style="themeStyle"，页面内的组件、弹层（DOM 上仍
 * 挂在页面根节点之内）与原生控件的 CSS 变量由此继承。微信小程序没有所有
 * 页面共享的 DOM 父节点，App.vue 的根样式不能替代逐页绑定。
 *
 * 用法（页面 <script setup> 顶部）：
 *   const { themeStyle, theme } = useMallTheme();
 *
 * composable 同时在页面显示时刷新已发布主题（恢复前台亦触发页面 onShow，
 * 与 App 级 onShow 的请求在 store 内合并）。页面私有的 onShow 不受影响。
 */
import { computed, watch } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import { useMallThemeStore } from '@/store/modules/mall/theme';

/**
 * 注入全局主题；只有声明共享顶部的可见页面才同步原生前景。
 * @param {object} options header=true表示使用主题顶部；scrolled为可空滚动状态ref，真值时使用白色阅读栏。
 * @returns {object} 当前主题、共享顶部及页面样式的只读计算值。
 */
export function useMallTheme({ header = false, scrolled = null } = {}) {
  const themeStore = useMallThemeStore();
  const headerGradient = computed(() => themeStore.headerGradient);
  let visible = false;
  function syncHeader() {
    // Tab页隐藏后仍保留监听，不能替当前白底业务页改成白色状态栏文字。
    if (!header || !visible) return;
    const reading = Boolean(scrolled?.value);
    uni.setNavigationBarColor({
      frontColor: !reading && headerGradient.value.foreground === '#FFFFFF' ? '#ffffff' : '#000000',
      backgroundColor: reading ? '#ffffff' : headerGradient.value.start,
    });
  }
  if (header) watch([headerGradient, () => scrolled?.value], syncHeader);

  onShow(() => {
    visible = true;
    syncHeader();
    themeStore.refresh();
  });
  onHide(() => {
    visible = false;
  });

  return {
    /** 当前主题配色对象（primary / dark / light / disabled），供 uni-icons 等无法消费 CSS 变量的场景 */
    theme: computed(() => themeStore.theme),
    themeCode: computed(() => themeStore.themeCode),
    headerGradient,
    /** 绑定到页面根节点的 CSS 变量 */
    themeStyle: computed(() => themeStore.themeStyle),
    /** 常用快捷值：原生控件 color 属性（switch 等）与图标着色 */
    primaryColor: computed(() => themeStore.theme.primary),
  };
}
