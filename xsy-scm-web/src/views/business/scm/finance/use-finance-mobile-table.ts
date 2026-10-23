import {onBeforeUnmount, onMounted, watch} from 'vue';

/** 桌面端固定操作列；窄屏改为随表格滚动，避免遮挡余额列。 */
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
