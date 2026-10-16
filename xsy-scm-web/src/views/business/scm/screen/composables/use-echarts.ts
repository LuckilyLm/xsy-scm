import * as echarts from 'echarts';
import {onBeforeUnmount, onMounted, type Ref} from 'vue';

export type ChartOption = Record<string, unknown>;

/** 管理图表的初始化、更新、尺寸变化和销毁。容器还没有大小时延后初始化，避免空白图表。 */
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
            // 元素尺寸为 0 时（v-show 隐藏）不做任何事：此时 setOption 会被 ensure() 挡下，
            // 配置只会留在 pending 里，等它重新可见时由下面那条分支补上。
            if (!el.clientWidth || !el.clientHeight) {
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
            // 已有实例但元素曾经被隐藏过：不能只 resize()。隐藏期间发生的数据变更
            // （例如首页切换指标时 v-show 短暂收起再展开）其 setOption 已被 ensure() 丢弃，
            // 只存进了 pending；若此处仅 resize()，画布会用旧配置重绘，表现为「切换了但图不变」。
            // 因此这里把最新配置重新应用一遍；setOption 自身会让画布适配当前尺寸。
            if (pending) {
                chart.setOption(pending, true);
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
