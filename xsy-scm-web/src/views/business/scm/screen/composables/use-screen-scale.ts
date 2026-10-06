import {onBeforeUnmount, onMounted, ref, type Ref} from 'vue';

/** 设计稿尺寸。所有布局常量都以这个尺寸为基准，缩放只改 transform，不改布局。 */
export const DESIGN_WIDTH = 1920;
export const DESIGN_HEIGHT = 1080;

/**
 * 1920×1080 设计稿等比缩放。
 *
 * 必须缩放而不是响应式：大屏每个面板高度都按 1080 精确分配，让浏览器自由伸缩会在
 * 某些视口算出负数或把内容挤没。等比缩放把「布局」与「适配」分开，适配只做一次 transform。
 *
 * 监听 wrapper 而不是 container：container 就是那个固定 1920×1080 的盒子，量它等于没量。
 * 用 ResizeObserver 而不是 window.onresize：进 / 退全屏时 window 不一定触发 resize，
 * 而 wrapper 尺寸一定变；且本组件是独立路由，卸载时可 unobserve，不留全局泄漏。
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

    return {scale, fit};
}
