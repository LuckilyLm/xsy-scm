import type {DirectiveBinding, VNode} from 'vue';
import {useUserStore} from '/@/store/modules/system/user';

const DENIED_TITLE = '暂无操作权限，请联系管理员';
const originalStates = new WeakMap<HTMLElement, {title: string | null; disabled: boolean}>();
const originalDisplays = new WeakMap<HTMLElement, string>();
const denialOwners = new WeakMap<HTMLElement, Set<HTMLElement>>();

/** 按钮组恢复权限时读取每个按钮当前的业务禁用条件。 */
function businessDisabled(vnode: VNode | undefined, button: HTMLElement): boolean | undefined {
  if (!vnode) return undefined;
  if (vnode.el === button && vnode.type === 'button') return Boolean(vnode.props?.disabled);
  if (vnode.component?.subTree) {
    const value = businessDisabled(vnode.component.subTree, button);
    if (value !== undefined) return value;
  }
  if (Array.isArray(vnode.children)) {
    for (const child of vnode.children) {
      if (!child || typeof child !== 'object') continue;
      const value = businessDisabled(child as VNode, button);
      if (value !== undefined) return value;
    }
  }
  return undefined;
}

/** 原生disabled之外再阻断事件，防止按钮外的确认框或菜单处理无权操作。 */
function blockAction(event: Event): void {
  event.preventDefault();
  event.stopImmediatePropagation();
}

/** 权限不足时保留操作按钮；表单、资料区域等内容继续按权限隐藏。 */
export function privilegeDirective(el: HTMLElement, binding: DirectiveBinding<string>, vnode?: VNode): void {
  const user = useUserStore();
  const allowed = user.administratorFlag || (user.getPointList ?? []).some((point: {webPerms?: string}) => point.webPerms === binding.value);
  const buttons = el.matches('button, a[role="button"]')
    ? [el]
    : el.classList.contains('ant-btn-group') || el.classList.contains('xsy-permission-action-group')
    ? Array.from(el.querySelectorAll<HTMLElement>('button, a[role="button"]'))
    : [];
  if (!buttons.length) {
    if (!allowed) {
      if (!originalDisplays.has(el)) originalDisplays.set(el, el.style.display);
      el.style.display = 'none';
    } else if (originalDisplays.has(el)) {
      el.style.display = originalDisplays.get(el) ?? '';
      originalDisplays.delete(el);
    }
    return;
  }
  for (const button of buttons) {
    if (!allowed) {
      const owners = denialOwners.get(button) ?? new Set<HTMLElement>();
      owners.add(el);
      denialOwners.set(button, owners);
      if (!originalStates.has(button))
        originalStates.set(button, {title: button.getAttribute('title'), disabled: button instanceof HTMLButtonElement && button.disabled});
      button.classList.add('xsy-permission-disabled');
      button.setAttribute('title', DENIED_TITLE);
      button.setAttribute('aria-disabled', 'true');
      if (button instanceof HTMLButtonElement) button.disabled = true;
      button.addEventListener('click', blockAction, true);
      button.addEventListener('keydown', blockAction, true);
    } else {
      const owners = denialOwners.get(button);
      owners?.delete(el);
      // 按钮组和按钮各自可能有权限约束，任意一处拒绝都不能被另一处启用。
      if (owners?.size || !originalStates.has(button)) continue;
      denialOwners.delete(button);
      button.classList.remove('xsy-permission-disabled');
      const state = originalStates.get(button)!;
      const title = state.title;
      if (title === null) button.removeAttribute('title');
      else if (title !== undefined) button.setAttribute('title', title);
      originalStates.delete(button);
      // 保留表单自身的禁用条件，不能因获得权限就启用仍在处理中的按钮。
      if (button instanceof HTMLButtonElement) button.disabled = businessDisabled(vnode, button) ?? state.disabled;
      button.removeAttribute('aria-disabled');
      button.removeEventListener('click', blockAction, true);
      button.removeEventListener('keydown', blockAction, true);
    }
  }
}
