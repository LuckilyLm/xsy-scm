import {onBeforeUnmount, onMounted, watch} from 'vue';

/** Keep the action rail pinned on desktop; let small screens scroll to it instead of covering balance columns. */
export function useFinanceMobileActionColumn(updateFixedAction: (compact: boolean) => void, columnsCount: () => number) {
    function update() {
        updateFixedAction(window.matchMedia('(max-width: 768px)').matches);
    }

    onMounted(() => {
        update();
        window.addEventListener('resize', update);
    });
    watch(columnsCount, update);
    onBeforeUnmount(() => window.removeEventListener('resize', update));
}
