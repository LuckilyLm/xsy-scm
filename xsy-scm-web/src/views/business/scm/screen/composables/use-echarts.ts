import * as echarts from 'echarts';
import {onBeforeUnmount, onMounted, type Ref} from 'vue';

export type ChartOption = Record<string, unknown>;

/**
 * 单个 ECharts 实例的生命周期管理（init / setOption / resize / dispose）。
 *
 * <p><b>为什么要处理「布局未就绪」</b>：面板高度是 flex 分配出来的，组件挂载的那一刻
 * 容器可能是 0×0。此时 `echarts.init` 会拿到 0×0 的画布，图完全不显示，
 * 而且控制台只给一条 can't get DOM width or height 的警告 —— 大屏上看就是「这块面板是空的」，
 * 很难定位。这里的做法是：元素还没有尺寸就**先记住 option 不 init**，
 * 等 ResizeObserver 报告出尺寸后再补 init + setOption。
 *
 * <p><b>为什么不需要在视口变化时 resize</b>：大屏整体是 transform: scale 缩放的，
 * 布局尺寸恒定 1920×1080，图表的 clientWidth/Height 从不变化。
 * 这里保留 resize 只为应对「面板本身尺寸变化」（字体加载、内容撑开）这类情况。
 */
export function useEcharts(elRef: Ref<HTMLElement | undefined>) {
    let chart: echarts.ECharts | null = null;
    let observer: ResizeObserver | null = null;
    // 尺寸就绪前收到的配置，等有尺寸了再补上
    let pending: ChartOption | null = null;

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
        if (instance) {
            instance.setOption(option, notMerge);
        }
    }

    function resize() {
        chart?.resize();
    }

    function dispose() {
        chart?.dispose();
        chart = null;
    }

    onMounted(() => {
        const el = elRef.value;
        if (typeof ResizeObserver === 'undefined' || !el) {
            return;
        }
        observer = new ResizeObserver(() => {
            if (!chart) {
                // 尺寸刚出现：补一次 init（配置从 pending 里拿）
                const instance = ensure();
                if (instance && pending) {
                    instance.setOption(pending, true);
                }
                return;
            }
            chart.resize();
        });
        observer.observe(el);
    });

    onBeforeUnmount(() => {
        observer?.disconnect();
        observer = null;
        // 必须 dispose：ECharts 实例持有 canvas 与全局事件，只置 null 会随路由反复切换而泄漏
        dispose();
    });

    return {setOption, resize, dispose};
}
