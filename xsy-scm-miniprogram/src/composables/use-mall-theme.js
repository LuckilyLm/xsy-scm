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
import { computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useMallThemeStore } from '@/store/modules/mall/theme';

export function useMallTheme() {
  const themeStore = useMallThemeStore();

  onShow(() => {
    themeStore.refresh();
  });

  return {
    /** 当前主题配色对象（primary / dark / light / disabled），供 uni-icons 等无法消费 CSS 变量的场景 */
    theme: computed(() => themeStore.theme),
    themeCode: computed(() => themeStore.themeCode),
    /** 绑定到页面根节点的 CSS 变量 */
    themeStyle: computed(() => themeStore.themeStyle),
    /** 常用快捷值：原生控件 color 属性（switch 等）与图标着色 */
    primaryColor: computed(() => themeStore.theme.primary),
  };
}
