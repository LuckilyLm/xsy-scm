/**
 * 数值输入框只接受数字、小数点和可选的负号。
 *
 * ant-design 的 InputNumber 在失焦前不拦截文本，字母也能敲进去，看起来和文本框没有区别；
 * 这里在按键和粘贴时过滤，让数值输入框在录入阶段就像数值输入框。
 *
 * 取值范围不在这里夹取：超出范围的输入保留原样，由表单校验给出提示。
 * 失焦时静默改成别的数字，用户不知道自己输入的东西被替换了。
 */

const EDIT_KEYS = new Set(['Backspace', 'Delete', 'Tab', 'Enter', 'Escape', 'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End']);

const NUMERIC_TEXT = /^-?\d*\.?\d*$/;

function numericField(target: EventTarget | null): HTMLInputElement | undefined {
  const input = target instanceof HTMLInputElement ? target : undefined;
  return input?.classList.contains('ant-input-number-input') ? input : undefined;
}

/** 整段选中时新字符会替换旧内容，不受现有点号、负号的限制。 */
function replacesAll(input: HTMLInputElement): boolean {
  return input.value !== '' && input.selectionStart === 0 && input.selectionEnd === input.value.length;
}

function acceptsKey(input: HTMLInputElement, key: string): boolean {
  if (/^\d$/.test(key)) return true;
  if (key === '.') return replacesAll(input) || !input.value.includes('.');
  if (key === '-') return input.selectionStart === 0 && (input.value === '' || replacesAll(input));
  return false;
}

function onKeydown(event: KeyboardEvent) {
  // 复制、粘贴、全选、撤销等组合键交给浏览器和组件自己处理
  if (event.ctrlKey || event.metaKey || event.altKey) return;
  const input = numericField(event.target);
  if (!input || EDIT_KEYS.has(event.key)) return;
  if (!acceptsKey(input, event.key)) event.preventDefault();
}

function onPaste(event: ClipboardEvent) {
  const input = numericField(event.target);
  if (!input) return;
  const text = (event.clipboardData?.getData('text') ?? '').replace(/\s/g, '');
  const start = input.selectionStart ?? input.value.length;
  const end = input.selectionEnd ?? input.value.length;
  const merged = input.value.slice(0, start) + text + input.value.slice(end);
  if (!NUMERIC_TEXT.test(merged)) event.preventDefault();
}

export function installNumericInputGuard(): void {
  document.addEventListener('keydown', onKeydown, true);
  document.addEventListener('paste', onPaste, true);
}
