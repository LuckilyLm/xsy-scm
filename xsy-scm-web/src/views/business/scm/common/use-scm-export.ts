import {computed, reactive} from 'vue';

/**
 * 导出按钮的 loading 至少停留多久。
 *
 * 演示数据下导出接口只要 20ms 左右，spinner 一闪而过 —— 用户读到的是「点了没反应」，
 * 于是再点一次，浏览器就多落一个文件。低于可感知阈值的 loading 等于没有 loading，
 * 所以这里给一个下限：请求多快都不少于 300ms（仍在「状态变化 200-300ms」这一档内）。
 */
const MIN_LOADING_MS = 300;

/** loading 状态的宿主：页面自己的对象，或报表 Tab 的视图对象（`TabView.exporting`）。 */
interface ExportFlag {
    exporting: boolean;
}

/**
 * 导出按钮的 loading 状态。
 *
 * 两件事必须一起做，缺一个都等于没反馈：
 * 1. 请求在飞的时候按钮进 loading —— antd 会同时把按钮禁用，挡住连点；
 * 2. loading 至少停留 MIN_LOADING_MS，否则就是一次看不见的闪烁。
 *
 * 传 `target` 时用调用方自己的状态位（报表页按 Tab 各存一份，一个 Tab 在导不让整排按钮都转）；
 * 不传就自带一个。失败仍由请求层的全局 toast 说明原因，这里不吞异常。
 *
 * ⚠️ 返回值必须用 `reactive` 包一层：模板里写的是 `xxxExport.exporting`，
 * 而 Vue 只解包顶层的 ref —— 返回 `{exporting: computed(...)}` 的话，
 * 模板拿到的是 ComputedRef 对象（恒为 truthy），按钮会永久 loading 且被禁用、点了没反应。
 */
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
