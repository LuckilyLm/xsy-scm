import * as echarts from 'echarts';
import {onBeforeUnmount, onMounted, watch, type Ref} from 'vue';

export type ChartOption = Record<string, unknown>;

/** 管理图表的初始化、更新、尺寸变化和销毁。容器还没有大小时延后初始化，避免空白图表。 */
export function useEcharts(elRef: Ref<HTMLElement | undefined>, {resizeOnAnimationFrame = false} = {}) {
  let chart: echarts.ECharts | null = null;
  let observer: ResizeObserver | null = null;
  // 尺寸就绪前收到的配置，等有尺寸了再补上
  let pending: ChartOption | null = null;
  let needsReplay = false;
  let frame = 0;

  function ensure(): echarts.ECharts | null {
    const el = elRef.value;
    if (!el || !el.clientWidth || !el.clientHeight) {
      return null;
    }
    if (!chart) {
      chart = echarts.init(el);
    }
    return chart;
  }

  function setOption(option: ChartOption, notMerge = true) {
    pending = option;
    const instance = ensure();
    needsReplay = !instance;
    if (instance) {
      instance.setOption(option, notMerge);
    }
  }

  function resize() {
    if (!resizeOnAnimationFrame) {
      chart?.resize();
      return;
    }
    if (frame) return;
    frame = requestAnimationFrame(() => {
      frame = 0;
      const replay = !chart || needsReplay;
      const instance = ensure();
      if (!instance) return;
      if (replay && pending) {
        instance.setOption(pending, true);
        needsReplay = false;
      }
      instance.resize();
    });
  }

  function dispose() {
    chart?.dispose();
    chart = null;
  }

  function observe(el: HTMLElement | undefined) {
    observer?.disconnect();
    if (resizeOnAnimationFrame && chart && chart.getDom() !== el) dispose();
    if (typeof ResizeObserver === 'undefined' || !el) {
      return;
    }
    observer = new ResizeObserver(() => {
      // 元素隐藏时尺寸为 0，配置留在 pending 里，等可见后再应用。
      if (!el.clientWidth || !el.clientHeight) {
        return;
      }
      if (resizeOnAnimationFrame) {
        resize();
        return;
      }
      if (!chart) {
        // 尺寸刚出现：补一次 init（配置从 pending 里拿）
        const instance = ensure();
        if (instance && pending) {
          instance.setOption(pending, true);
        }
        return;
      }
      // 隐藏期间的数据变更只存在 pending 里，恢复显示时要重新应用。
      if (pending) {
        chart.setOption(pending, true);
      }
      chart.resize();
    });
    observer.observe(el);
  }

  onMounted(() => {
    observe(elRef.value);
    if (resizeOnAnimationFrame) {
      window.addEventListener('resize', resize);
      document.addEventListener('fullscreenchange', resize);
    }
  });

  if (resizeOnAnimationFrame) {
    watch(
      elRef,
      (el) => {
        observe(el);
        resize();
      },
      {flush: 'post'}
    );
  }

  onBeforeUnmount(() => {
    cancelAnimationFrame(frame);
    window.removeEventListener('resize', resize);
    document.removeEventListener('fullscreenchange', resize);
    observer?.disconnect();
    observer = null;
    // 必须 dispose，否则切换路由会泄漏 canvas 和事件。
    dispose();
  });

  return {setOption, resize, dispose};
}
