import {onBeforeUnmount, onMounted, ref, type Ref} from 'vue';

/** 设计稿尺寸。所有布局常量都以这个尺寸为基准，缩放只改 transform，不改布局。 */
export const DESIGN_WIDTH = 1920;
export const DESIGN_HEIGHT = 1080;

/** 大屏以 1920×1080 为设计尺寸，按视口等比缩放，避免面板被浏览器响应式布局挤压。 */
export function useScreenScale(wrapperRef: Ref<HTMLElement | undefined>, containerRef: Ref<HTMLElement | undefined>) {
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
    // 容器是 position:absolute + left/top:50%，先把它自己的中心挪到 wrapper 中心，
    // 再缩放。translate 的百分比按容器自身尺寸（恒为 1920×1080）计算，
    // 因此与 scale 的乘法顺序必须是「先平移后缩放」——
    // 写成 translate(-50%,-50%) scale(s) 才能让缩放围绕容器中心进行。
    // 只写 scale(s) 会让容器左上角停在 wrapper 中心，画面整体偏到右下角。
    container.style.transform = `translate(-50%, -50%) scale(${next})`;
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

  return {scale, fit};
}
