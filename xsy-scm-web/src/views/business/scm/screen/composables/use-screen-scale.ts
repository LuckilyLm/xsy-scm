import {onBeforeUnmount, onMounted, ref, type Ref} from 'vue';

export const DESIGN_WIDTH = 1920;
export const DESIGN_HEIGHT = 1080;
const COMPACT_WIDTH = 1600;
const COMPACT_HEIGHT = 900;

/** 逻辑画布随视口扩展，缩放系数只由较紧的一边决定。 */
export function getScreenLayout(width: number, height: number) {
  if (width <= 0 || height <= 0) return null;
  const compact = width < DESIGN_WIDTH || height < DESIGN_HEIGHT;
  const scale = compact ? Math.min(1, width / COMPACT_WIDTH, height / COMPACT_HEIGHT) : Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT);
  return {width: width / scale, height: height / scale, scale, compact};
}

export function useScreenScale(wrapperRef: Ref<HTMLElement | undefined>, containerRef: Ref<HTMLElement | undefined>) {
  const scale = ref(1);
  let observer: ResizeObserver | null = null;

  function fit() {
    const wrapper = wrapperRef.value;
    const container = containerRef.value;
    if (!wrapper || !container) return;
    const layout = getScreenLayout(wrapper.clientWidth, wrapper.clientHeight);
    if (!layout) return;
    scale.value = layout.scale;
    container.dataset.density = layout.compact ? 'compact' : 'regular';
    container.style.width = `${layout.width}px`;
    container.style.height = `${layout.height}px`;
    container.style.transform = `translate(-50%, -50%) scale(${layout.scale})`;
  }

  onMounted(() => {
    fit();
    if (typeof ResizeObserver !== 'undefined' && wrapperRef.value) {
      observer = new ResizeObserver(fit);
      observer.observe(wrapperRef.value);
    }
    window.addEventListener('resize', fit);
    document.addEventListener('fullscreenchange', fit);
  });

  onBeforeUnmount(() => {
    observer?.disconnect();
    window.removeEventListener('resize', fit);
    document.removeEventListener('fullscreenchange', fit);
  });

  return {scale, fit};
}
