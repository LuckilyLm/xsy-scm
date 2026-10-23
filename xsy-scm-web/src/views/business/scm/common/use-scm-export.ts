import {computed, reactive} from 'vue';

/** 导出时的最短 loading 时长，避免成功时一闪而过。 */
const MIN_LOADING_MS = 300;

/** loading 状态的宿主：页面自己的对象，或报表 Tab 的视图对象（`TabView.exporting`）。 */
interface ExportFlag {
  exporting: boolean;
}

/** 导出期间保持 loading 并防止重复点击。 */
export function useScmExport(target?: ExportFlag) {
  const state: ExportFlag = target ?? reactive({exporting: false});
  const exporting = computed(() => state.exporting);

  async function run(task: () => Promise<unknown>): Promise<void> {
    if (state.exporting) {
      return;
    }
    state.exporting = true;
    const started = Date.now();
    try {
      await task();
    } finally {
      const rest = MIN_LOADING_MS - (Date.now() - started);
      if (rest > 0) {
        await new Promise((resolve) => setTimeout(resolve, rest));
      }
      state.exporting = false;
    }
  }

  return reactive({exporting, run});
}
