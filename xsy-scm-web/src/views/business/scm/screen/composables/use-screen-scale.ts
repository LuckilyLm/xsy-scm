import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue';

/** 设计稿尺寸。所有布局常量都以这个尺寸为基准，缩放只改 transform，不改布局。 */
export const DESIGN_WIDTH = 1920;
export const DESIGN_HEIGHT = 1080;

/**
 * 1920×1080 设计稿等比缩放。
 *
 * <p>为什么必须缩放而不是「响应式布局」：大屏的每个面板高度都是按 1080 精确分配的
 * （420 / 1000 / 420 三列 + 208 趋势带），一旦让浏览器自由伸缩，面板高度会在
 * 某些视口下算出负数或把内容挤没。等比缩放把「布局」和「适配」彻底分开：
 * 布局永远按设计稿算，适配只做一次整体 transform。
 *
 * <p>为什么监听 wrapper 而不是 container：container 就是那个 1920×1080 的固定盒子，
 * 它的 clientWidth/Height 永远是 1920/1080，量它等于没量。可用空间在 wrapper 上。
 *
 * <p>为什么用 ResizeObserver 而不是 window.onresize：进入/退出全屏时 window 不一定触发
 * resize（取决于浏览器），但 wrapper 的尺寸一定变。另外本组件是独立路由，
 * 用 ResizeObserver 可以在卸载时 unobserve，不会留下像 smart-watermark 那样的全局泄漏。
 */
export function useScreenScale(
  wrapperRef: Ref<HTMLElement | undefined>,
  containerRef: Ref<HTMLElement | undefined>
) {
  const scale = ref(1);
  let observer: ResizeObserver | null = null;

  function fit() {
    const wrapper = wrapperRef.value;
    const container = containerRef.value;
    if (!wrapper || !container) {
      return;
    }
    const availableWidth = wrapper.clientWidth;
    const availableHeight = wrapper.clientHeight;
    if (!availableWidth || !availableHeight) {
      return;
    }
    // 取较小比例：宁可上下留黑边，也不能让内容超出被裁掉
    const next = Math.min(availableWidth / DESIGN_WIDTH, availableHeight / DESIGN_HEIGHT);
    scale.value = next;
    container.style.transform = `scale(${next})`;
  }

  onMounted(() => {
    fit();
    if (typeof ResizeObserver !== 'undefined' && wrapperRef.value) {
      observer = new ResizeObserver(() => fit());
      observer.observe(wrapperRef.value);
    }
    // ResizeObserver 在部分浏览器的全屏切换中不会立刻回调，兜一层
    window.addEventListener('resize', fit);
  });

  onBeforeUnmount(() => {
    observer?.disconnect();
    observer = null;
    window.removeEventListener('resize', fit);
  });

  return { scale, fit };
}
