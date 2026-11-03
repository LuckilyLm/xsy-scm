import type {Directive} from 'vue';

const CONTROL_SELECTOR = 'input:not([type="hidden"]), textarea, select, button, [role="combobox"]';

function applyAriaLabel(el: HTMLElement, value: unknown): void {
  if (typeof value !== 'string' || !value.trim()) return;
  const control = el.matches(CONTROL_SELECTOR) ? el : el.querySelector<HTMLElement>(CONTROL_SELECTOR);
  control?.setAttribute('aria-label', value);
}

/**
 * Ant Design 组件会把未声明的属性留在根节点，`aria-label` 到不了真正可聚焦的输入框。
 * 这个指令把名称写到组件内部第一个表单控件上。
 */
export const ariaLabelDirective: Directive<HTMLElement, string> = {
  mounted: (el, binding) => applyAriaLabel(el, binding.value),
  updated: (el, binding) => applyAriaLabel(el, binding.value),
};
